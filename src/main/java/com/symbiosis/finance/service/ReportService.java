package com.symbiosis.finance.service;

import com.symbiosis.finance.mapper.ReportMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 三大财务报表取数与计算服务（小企业会计准则）。
 *
 * 约定：
 *  - 余额方向：asset/expense 类余额 = 借方 - 贷方；liability/equity/income 类余额 = 贷方 - 借方。
 *  - 仅统计 status='posted' AND deleted=0 的凭证（由 Mapper 的 SQL 保证）。
 *  - 所有查询按 v.user_id（UUID）过滤。
 *  - 现金科目：仅 1002001（银行存款）、1002002（库存现金）。
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    // ════════════════════════════════════════════════════════════════
    //  辅助方法
    // ════════════════════════════════════════════════════════════════

    /** 金额统一保留 2 位小数，四舍五入。 */
    private static BigDecimal scale(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    /** null 安全取 BigDecimal。 */
    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 把 sumByAccount 的结果整理成「科目编号 -> 余额」。
     * 余额按科目性质（accountType）计算方向：
     *   asset/expense  -> 借方 - 贷方
     *   liability/equity/income -> 贷方 - 借方
     * 其他类型按借-贷处理。缺失科目默认 0。
     */
    private Map<String, BigDecimal> balanceMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String account = String.valueOf(row.get("account"));
            String type = String.valueOf(row.getOrDefault("account_type", "expense"));
            BigDecimal debit = toBd(row.get("sum_debit"));
            BigDecimal credit = toBd(row.get("sum_credit"));
            BigDecimal balance;
            if ("liability".equalsIgnoreCase(type)
                    || "equity".equalsIgnoreCase(type)
                    || "income".equalsIgnoreCase(type)) {
                balance = credit.subtract(debit);    // 贷 - 借
            } else {
                balance = debit.subtract(credit);    // asset/expense: 借 - 借
            }
            map.put(account, scale(balance));
        }
        return map;
    }

    /**
     * 把 openingBalance 的结果整理成「科目编号 -> 期初余额」。
     * 资产类取 debit_balance，负债/权益类取 credit_balance；
     * 报表内对资产/负债权益分别调用此方法并指定方向。
     */
    private Map<String, BigDecimal> openingMap(List<Map<String, Object>> rows,
                                               boolean creditSide) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            String account = String.valueOf(row.get("account_id"));
            BigDecimal val = creditSide
                    ? toBd(row.get("credit_balance"))
                    : toBd(row.get("debit_balance"));
            map.put(account, scale(val));
        }
        return map;
    }

    /** 从结果集取一个数值列（兼容 BigDecimal/Number）。 */
    private static BigDecimal toBd(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(o.toString());
    }

    /** 从余额表安全取值，缺失返回 0。 */
    private static BigDecimal get(Map<String, BigDecimal> map, String key) {
        return nz(map.get(key));
    }

    // ════════════════════════════════════════════════════════════════
    //  利润表 IncomeStatement
    // ════════════════════════════════════════════════════════════════

    /**
     * 利润表：取 income/expense 类期间发生额。
     *
     * @return 按行次有序的报表行，每行 {row, item, amount, formula}
     */
    public List<Map<String, Object>> incomeStatement(UUID userId, String startDate, String endDate) {
        Map<String, BigDecimal> m = balanceMap(reportMapper.sumByAccount(userId, startDate, endDate));

        // 1 营业收入 = 主营业务收入 + 其他业务收入（income 类，贷-借）
        BigDecimal revenue = get(m, "4001001").add(get(m, "4001002"));

        // 2 营业成本 = 主营业务成本 + 其他业务成本（expense 类，借-贷）
        BigDecimal cost = get(m, "5001011").add(get(m, "5001012"));

        // 3 税金及附加：本系统未设该科目，填 0
        BigDecimal taxSurcharge = BigDecimal.ZERO;

        // 11 销售费用：本系统未设单独的销售费用科目，填 0
        BigDecimal sellingExpense = BigDecimal.ZERO;

        // 14 管理费用 = 办公费 + 差旅费 + 招待费 + 水电费 + 物业费 + 租赁费 + 技术服务费 + 物流费 + 折旧费
        //    （expense 类，借-贷）。职工薪酬(5001010)单列，不计入此处。
        BigDecimal adminExpense = get(m, "5001001").add(get(m, "5001002"))
                .add(get(m, "5001003")).add(get(m, "5001004"))
                .add(get(m, "5001005")).add(get(m, "5001006"))
                .add(get(m, "5001007")).add(get(m, "5001008"))
                .add(get(m, "5001009"));

        // 18 财务费用：本系统暂无，取 0
        BigDecimal financeExpense = BigDecimal.ZERO;

        // 21 营业利润 = 营业收入 - 营业成本 - 税金及附加 - 销售费用 - 管理费用 - 财务费用 + 投资收益
        BigDecimal investIncome = get(m, "4001006");   // income 类，贷-借
        BigDecimal operatingProfit = revenue
                .subtract(cost)
                .subtract(taxSurcharge)
                .subtract(sellingExpense)
                .subtract(adminExpense)
                .subtract(financeExpense)
                .add(investIncome);

        // 22 营业外收入
        BigDecimal nonOpIncome = get(m, "4001003");
        // 24 营业外支出（暂无科目）
        BigDecimal nonOpExpense = BigDecimal.ZERO;

        // 30 利润总额
        BigDecimal totalProfit = operatingProfit.add(nonOpIncome).subtract(nonOpExpense);
        // 31 所得税费用（暂未取数）
        BigDecimal incomeTax = BigDecimal.ZERO;
        // 32 净利润
        BigDecimal netProfit = totalProfit.subtract(incomeTax);

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(row(1, "营业收入", revenue, "主营业务收入(4001001)+其他业务收入(4001002)"));
        rows.add(row(2, "营业成本", cost, "主营业务成本(5001011)+其他业务成本(5001012)"));
        rows.add(row(3, "税金及附加", taxSurcharge, "本系统未设该科目，取0"));
        rows.add(row(11, "销售费用", sellingExpense, "本系统未设单独的销售费用科目，取0"));
        rows.add(row(14, "管理费用", adminExpense,
                "办公费(5001001)+差旅费(5001002)+招待费(5001003)+水电费(5001004)+物业费(5001005)+租赁费(5001006)+技术服务费(5001007)+物流费(5001008)+折旧费(5001009)"));
        rows.add(row(18, "财务费用", financeExpense, "本系统暂无，取0"));
        rows.add(row(21, "营业利润", operatingProfit,
                "营业收入-营业成本-税金及附加-销售费用-管理费用-财务费用+投资收益(4001006)"));
        rows.add(row(22, "营业外收入", nonOpIncome, "营业外收入(4001003)"));
        rows.add(row(24, "营业外支出", nonOpExpense, "暂无科目，取0"));
        rows.add(row(30, "利润总额", totalProfit, "营业利润+营业外收入-营业外支出"));
        rows.add(row(31, "所得税费用", incomeTax, "暂取0"));
        rows.add(row(32, "净利润", netProfit, "利润总额-所得税费用"));
        return rows;
    }

    // ════════════════════════════════════════════════════════════════
    //  资产负债表 BalanceSheet
    // ════════════════════════════════════════════════════════════════

    /**
     * 资产负债表：取 asset/liability/equity 类期末余额（voucher_date <= endDate），
     * 叠加期初余额（openingBalance，取 endDate 年份）。
     *
     * @return Map { assets:[...], liabilities:[...] }，每行 {row, item, endBalance, beginBalance, formula}
     */
    public Map<String, List<Map<String, Object>>> balanceSheet(UUID userId, String endDate) {
        // 期末余额：累计到 endDate
        // 起始日期取该年初，用于取「本年累计发生额」并叠加期初。
        int year = LocalDate.parse(endDate).getYear();
        String yearStart = year + "-01-01";

        Map<String, BigDecimal> end = balanceMap(reportMapper.sumByAccount(userId, yearStart, endDate));

        // 期初余额：资产类取 debit_balance
        Map<String, BigDecimal> beginAsset =
                openingMap(reportMapper.openingBalance(userId, year), false);
        // 期初余额：负债/权益类取 credit_balance
        Map<String, BigDecimal> beginEquity =
                openingMap(reportMapper.openingBalance(userId, year), true);

        // —— 资产（左边，asset 类，期末=借-贷，期初用 debit_balance）——
        List<Map<String, Object>> assets = new java.util.ArrayList<>();

        // 1 货币资金
        BigDecimal cashEnd = get(end, "1002002").add(get(end, "1002001")).add(get(end, "1002006"));
        BigDecimal cashBegin = get(beginAsset, "1002002").add(get(beginAsset, "1002001")).add(get(beginAsset, "1002006"));
        assets.add(bsRow(1, "货币资金", cashEnd, cashBegin,
                "库存现金(1002002)+银行存款(1002001)+其他货币资金(1002006)"));

        // 4 应收账款 = 应收账款 - 坏账准备
        BigDecimal arEnd = get(end, "1002003").subtract(get(end, "1002011"));
        BigDecimal arBegin = get(beginAsset, "1002003").subtract(get(beginAsset, "1002011"));
        assets.add(bsRow(4, "应收账款", arEnd, arBegin, "应收账款(1002003)-坏账准备(1002011)"));

        // 5 预付账款
        assets.add(bsRow(5, "预付账款", get(end, "1002004"), get(beginAsset, "1002004"), "预付账款(1002004)"));

        // 8 其他应收款
        assets.add(bsRow(8, "其他应收款", get(end, "1002005"), get(beginAsset, "1002005"), "其他应收款(1002005)"));

        // 9 存货
        BigDecimal invEnd = get(end, "1002012").add(get(end, "1002013")).add(get(end, "1002014"))
                .add(get(end, "1002015")).add(get(end, "1002016")).add(get(end, "1002017"))
                .add(get(end, "1002018")).add(get(end, "1002019"))
                .subtract(get(end, "1002020"))
                .add(get(end, "1002021"))
                .add(get(end, "1002022"));
        BigDecimal invBegin = get(beginAsset, "1002012").add(get(beginAsset, "1002013")).add(get(beginAsset, "1002014"))
                .add(get(beginAsset, "1002015")).add(get(beginAsset, "1002016")).add(get(beginAsset, "1002017"))
                .add(get(beginAsset, "1002018")).add(get(beginAsset, "1002019"))
                .subtract(get(beginAsset, "1002020"))
                .add(get(beginAsset, "1002021"))
                .add(get(beginAsset, "1002022"));
        assets.add(bsRow(9, "存货", invEnd, invBegin,
                "原材料+周转材料+在途物资+材料采购+委托加工物资+生产成本+库存商品+发出商品-存货跌价准备±材料成本差异±商品进销差价"));

        // 15 流动资产合计 = 上述流动资产各项之和
        BigDecimal curAssetEnd = cashEnd.add(arEnd).add(get(end, "1002004"))
                .add(get(end, "1002005")).add(invEnd);
        BigDecimal curAssetBegin = cashBegin.add(arBegin).add(get(beginAsset, "1002004"))
                .add(get(beginAsset, "1002005")).add(invBegin);
        assets.add(bsRow(15, "流动资产合计", curAssetEnd, curAssetBegin,
                "货币资金+应收账款+预付账款+其他应收款+存货"));

        // 18 固定资产原价 = 固定资产 + 办公设备 + 电子设备
        BigDecimal faCostEnd = get(end, "1003006").add(get(end, "1003001")).add(get(end, "1003002"));
        BigDecimal faCostBegin = get(beginAsset, "1003006").add(get(beginAsset, "1003001")).add(get(beginAsset, "1003002"));
        assets.add(bsRow(18, "固定资产原价", faCostEnd, faCostBegin,
                "固定资产(1003006)+办公设备(1003001)+电子设备(1003002)"));

        // 19 减：累计折旧（资产类但余额为贷方，取绝对值显示）
        BigDecimal depEnd = get(end, "1003003").abs();
        BigDecimal depBegin = get(beginAsset, "1003003").abs();
        assets.add(bsRow(19, "减：累计折旧", depEnd, depBegin, "累计折旧(1003003)，取绝对值"));

        // 20 固定资产账面价值 = 原价 - 累计折旧
        BigDecimal faNetEnd = faCostEnd.subtract(depEnd);
        BigDecimal faNetBegin = faCostBegin.subtract(depBegin);
        assets.add(bsRow(20, "固定资产账面价值", faNetEnd, faNetBegin, "固定资产原价-累计折旧"));

        // 25 无形资产 = 无形资产 - 累计摊销
        BigDecimal iaEnd = get(end, "1003010").subtract(get(end, "1003011"));
        BigDecimal iaBegin = get(beginAsset, "1003010").subtract(get(beginAsset, "1003011"));
        assets.add(bsRow(25, "无形资产", iaEnd, iaBegin, "无形资产(1003010)-累计摊销(1003011)"));

        // 29 非流动资产合计 = 固定资产账面价值 + 无形资产 + 长期待摊费用 + 其他非流动资产
        BigDecimal ltEnd = get(end, "1003013");
        BigDecimal ltBegin = get(beginAsset, "1003013");
        BigDecimal otherNonCurEnd = get(end, "1003005");   // 长期股权投资等（简化加总）
        BigDecimal otherNonCurBegin = get(beginAsset, "1003005");
        BigDecimal nonCurEnd = faNetEnd.add(iaEnd).add(ltEnd).add(otherNonCurEnd);
        BigDecimal nonCurBegin = faNetBegin.add(iaBegin).add(ltBegin).add(otherNonCurBegin);
        assets.add(bsRow(29, "非流动资产合计", nonCurEnd, nonCurBegin,
                "固定资产账面价值+无形资产+长期待摊费用(1003013)+其他(长期股权投资1003005等)"));

        // 30 资产总计
        BigDecimal totalAssetEnd = curAssetEnd.add(nonCurEnd);
        BigDecimal totalAssetBegin = curAssetBegin.add(nonCurBegin);
        assets.add(bsRow(30, "资产总计", totalAssetEnd, totalAssetBegin, "流动资产合计+非流动资产合计"));

        // —— 负债和所有者权益（右边，liability/equity 类，期末=贷-借，期初用 credit_balance）——
        List<Map<String, Object>> liabilities = new java.util.ArrayList<>();

        liabilities.add(bsRow(31, "短期借款", get(end, "2002006"), get(beginEquity, "2002006"), "短期借款(2002006)"));
        liabilities.add(bsRow(33, "应付账款", get(end, "2002001"), get(beginEquity, "2002001"), "应付账款(2002001)"));
        liabilities.add(bsRow(34, "预收账款", get(end, "2002002"), get(beginEquity, "2002002"), "预收账款(2002002)"));
        liabilities.add(bsRow(35, "应付职工薪酬", get(end, "2002003"), get(beginEquity, "2002003"), "应付职工薪酬(2002003)"));
        liabilities.add(bsRow(36, "应交税费", get(end, "2002004"), get(beginEquity, "2002004"), "应交税费(2002004)"));
        liabilities.add(bsRow(38, "其他应付款", get(end, "2002005"), get(beginEquity, "2002005"), "其他应付款(2002005)"));

        // 40 流动负债合计
        BigDecimal curLiabEnd = get(end, "2002006").add(get(end, "2002001")).add(get(end, "2002002"))
                .add(get(end, "2002003")).add(get(end, "2002004")).add(get(end, "2002005"));
        BigDecimal curLiabBegin = get(beginEquity, "2002006").add(get(beginEquity, "2002001")).add(get(beginEquity, "2002002"))
                .add(get(beginEquity, "2002003")).add(get(beginEquity, "2002004")).add(get(beginEquity, "2002005"));
        liabilities.add(bsRow(40, "流动负债合计", curLiabEnd, curLiabBegin, "上述各项流动负债之和"));

        // 41 长期借款、42 长期应付款、45 非流动负债合计
        BigDecimal ltLoanEnd = get(end, "2003001");
        BigDecimal ltLoanBegin = get(beginEquity, "2003001");
        BigDecimal ltPayEnd = get(end, "2003002");
        BigDecimal ltPayBegin = get(beginEquity, "2003002");
        liabilities.add(bsRow(41, "长期借款", ltLoanEnd, ltLoanBegin, "长期借款(2003001)"));
        liabilities.add(bsRow(42, "长期应付款", ltPayEnd, ltPayBegin, "长期应付款(2003002)"));
        BigDecimal nonCurLiabEnd = ltLoanEnd.add(ltPayEnd);
        BigDecimal nonCurLiabBegin = ltLoanBegin.add(ltPayBegin);
        liabilities.add(bsRow(45, "非流动负债合计", nonCurLiabEnd, nonCurLiabBegin, "长期借款+长期应付款"));

        // 46 负债合计
        BigDecimal totalLiabEnd = curLiabEnd.add(nonCurLiabEnd);
        BigDecimal totalLiabBegin = curLiabBegin.add(nonCurLiabBegin);
        liabilities.add(bsRow(46, "负债合计", totalLiabEnd, totalLiabBegin, "流动负债合计+非流动负债合计"));

        // 47 实收资本、48 资本公积、49 盈余公积
        liabilities.add(bsRow(47, "实收资本", get(end, "3001001"), get(beginEquity, "3001001"), "实收资本(3001001)"));
        liabilities.add(bsRow(48, "资本公积", get(end, "3001003"), get(beginEquity, "3001003"), "资本公积(3001003)"));
        liabilities.add(bsRow(49, "盈余公积", get(end, "3001004"), get(beginEquity, "3001004"), "盈余公积(3001004)"));

        // 本年利润自动结转：当年净利润（收入-费用）注入权益侧，使报表自动平衡
        // 净利润 = 当年收入类发生额 - 当年费用类发生额
        BigDecimal curYearProfit = BigDecimal.ZERO;
        Map<String, BigDecimal> curYear = balanceMap(reportMapper.sumByAccount(userId, yearStart, endDate));
        for (Map.Entry<String, BigDecimal> e2 : curYear.entrySet()) {
            String acc = e2.getKey();
            // income 类（4开头）增加利润，expense 类（5开头）减少利润
            if (acc.startsWith("4")) curYearProfit = curYearProfit.add(e2.getValue());
            else if (acc.startsWith("5")) curYearProfit = curYearProfit.subtract(e2.getValue());
        }
        // 本年利润余额 = 科目余额(若有结转) + 当年净利润（未结转部分自动补入）
        BigDecimal curProfit = get(end, "3001005").add(curYearProfit);

        // 50 未分配利润 = 利润分配(3001006) + 本年利润(3001005)
        BigDecimal retainEnd = get(end, "3001006").add(curProfit);
        BigDecimal retainBegin = get(beginEquity, "3001006").add(get(beginEquity, "3001005"));
        liabilities.add(bsRow(50, "未分配利润", retainEnd, retainBegin,
                "利润分配(3001006)+本年利润(3001005)"));

        // 51 所有者权益合计
        BigDecimal equityEnd = get(end, "3001001").add(get(end, "3001003"))
                .add(get(end, "3001004")).add(retainEnd);
        BigDecimal equityBegin = get(beginEquity, "3001001").add(get(beginEquity, "3001003"))
                .add(get(beginEquity, "3001004")).add(retainBegin);
        liabilities.add(bsRow(51, "所有者权益合计", equityEnd, equityBegin,
                "实收资本+资本公积+盈余公积+未分配利润"));

        // 52 负债和所有者权益总计
        BigDecimal totalEnd = totalLiabEnd.add(equityEnd);
        BigDecimal totalBegin = totalLiabBegin.add(equityBegin);
        liabilities.add(bsRow(52, "负债和所有者权益总计", totalEnd, totalBegin, "负债合计+所有者权益合计"));

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("assets", assets);
        result.put("liabilities", liabilities);
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  现金流量表 CashFlow（简化版）
    // ════════════════════════════════════════════════════════════════

    /**
     * 现金流量表（简化版）：基于现金科目（1002001/1002002）发生额的间接映射。
     * 直接法难以精确拆分到每个行次（需要辅助核算），故此处：
     *  - 经营/投资/筹资活动行次按相关科目发生额粗略映射，无法精确拆分的标 0；
     *  - 20 现金净增加额 = 期末现金余额 - 期初现金余额；
     *  - 22 期末现金余额 = 现金科目期末余额。
     *
     * @return 按行次有序的报表行，每行 {row, item, amount, formula}
     */
    public List<Map<String, Object>> cashFlow(UUID userId, String startDate, String endDate) {
        // 期间发生额（startDate ~ endDate）
        Map<String, BigDecimal> m = balanceMap(reportMapper.sumByAccount(userId, startDate, endDate));

        // 现金流入 = 现金科目借方发生额；现金流出 = 现金科目贷方发生额。
        // 这里直接从原始结果取借贷合计，避免方向被 balanceMap 折算。
        BigDecimal cashIn = BigDecimal.ZERO;
        BigDecimal cashOut = BigDecimal.ZERO;
        List<Map<String, Object>> raw = reportMapper.sumByAccount(userId, startDate, endDate);
        if (raw != null) {
            for (Map<String, Object> row : raw) {
                String account = String.valueOf(row.get("account"));
                if ("1002001".equals(account) || "1002002".equals(account)) {
                    cashIn = cashIn.add(toBd(row.get("sum_debit")));
                    cashOut = cashOut.add(toBd(row.get("sum_credit")));
                }
            }
        }
        BigDecimal netCash = cashIn.subtract(cashOut);

        // 期末现金余额 = 现金科目期末余额（年初至 endDate 的累计余额 + 期初）
        int year = LocalDate.parse(endDate).getYear();
        String yearStart = year + "-01-01";
        Map<String, BigDecimal> endBal = balanceMap(reportMapper.sumByAccount(userId, yearStart, endDate));
        Map<String, BigDecimal> beginAsset = openingMap(reportMapper.openingBalance(userId, year), false);
        BigDecimal cashEndBalance = get(endBal, "1002001").add(get(endBal, "1002002"))
                .add(get(beginAsset, "1002001")).add(get(beginAsset, "1002002"));
        BigDecimal cashBeginBalance = get(beginAsset, "1002001").add(get(beginAsset, "1002002"));
        BigDecimal cashNetIncrease = cashEndBalance.subtract(cashBeginBalance);

        // 投资活动：长期资产相关科目（1003xxx/1004xxx）发生额（简化，仅作参考）
        BigDecimal investActivity = BigDecimal.ZERO;
        // 筹资活动：借款/实收资本科目发生额（简化，仅作参考）
        BigDecimal financeActivity = BigDecimal.ZERO;
        if (raw != null) {
            for (Map<String, Object> row : raw) {
                String account = String.valueOf(row.get("account"));
                BigDecimal amt = nz(m.get(account)).abs();
                if (account.startsWith("1003") || account.startsWith("1004")) {
                    investActivity = investActivity.add(amt);
                } else if ("2002006".equals(account) || "2003001".equals(account)
                        || "2003002".equals(account) || "3001001".equals(account)) {
                    financeActivity = financeActivity.add(amt);
                }
            }
        }

        // 经营活动：现金净额扣除可识别的投资/筹资部分（简化）
        BigDecimal operatingNet = netCash.subtract(investActivity).subtract(financeActivity);

        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        rows.add(row(1, "销售商品、提供劳务收到的现金", BigDecimal.ZERO,
                "需辅助核算方可精确拆分，暂取0"));
        rows.add(row(6, "经营活动现金流入小计", BigDecimal.ZERO, "需辅助核算，暂取0"));
        rows.add(row(7, "购买商品、接受劳务支付的现金", BigDecimal.ZERO,
                "需辅助核算方可精确拆分，暂取0"));
        rows.add(row(11, "经营活动现金流出小计", BigDecimal.ZERO, "需辅助核算，暂取0"));
        rows.add(row(12, "经营活动产生的现金流量净额", scale(operatingNet),
                "现金净额-投资活动-筹资活动（简化，精确值需辅助核算）"));
        rows.add(row(13, "投资活动产生的现金流量净额", scale(investActivity.negate()),
                "长期资产科目(1003xxx/1004xxx)发生额估算，精确值需辅助核算"));
        rows.add(row(16, "筹资活动产生的现金流量净额", scale(financeActivity),
                "借款/实收资本科目发生额估算，精确值需辅助核算"));
        rows.add(row(20, "现金净增加额", scale(cashNetIncrease), "期末现金余额-期初现金余额"));
        rows.add(row(21, "期初现金余额", scale(cashBeginBalance),
                "库存现金(1002002)+银行存款(1002001)期初余额"));
        rows.add(row(22, "期末现金余额", scale(cashEndBalance),
                "库存现金(1002002)+银行存款(1002001)期末余额"));
        return rows;
    }

    // ════════════════════════════════════════════════════════════════
    //  报表行构造
    // ════════════════════════════════════════════════════════════════

    /** 利润表 / 现金流量表行：{row, item, amount, formula} */
    private static Map<String, Object> row(int row, String item, BigDecimal amount, String formula) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("row", row);
        r.put("item", item);
        r.put("amount", scale(amount));
        r.put("formula", formula);
        return r;
    }

    /** 资产负债表行：{row, item, endBalance, beginBalance, formula} */
    private static Map<String, Object> bsRow(int row, String item,
                                             BigDecimal endBalance, BigDecimal beginBalance, String formula) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("row", row);
        r.put("item", item);
        r.put("endBalance", scale(endBalance));
        r.put("beginBalance", scale(beginBalance));
        r.put("formula", formula);
        return r;
    }
}
