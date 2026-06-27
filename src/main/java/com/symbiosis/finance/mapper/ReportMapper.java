package com.symbiosis.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 三大财务报表取数 Mapper。
 * 所有查询仅取已过账（status='posted'）且未删除（deleted=0）的凭证，
 * 并按 user_id（UUID）做用户隔离。
 */
@Mapper
public interface ReportMapper {

    /**
     * 按科目汇总某期间内的借方/贷方发生额。
     * 仅统计 posted 且未删除的凭证。
     *
     * @return 每行含 account / accountType / accountName / sumDebit / sumCredit
     */
    @Select("SELECT ve.account            AS account, " +
            "       a.account_type        AS account_type, " +
            "       a.name                AS accountName, " +
            "       SUM(ve.debit_amount)  AS sum_debit, " +
            "       SUM(ve.credit_amount) AS sum_credit " +
            "FROM voucher_entries ve " +
            "JOIN vouchers  v ON v.id = ve.voucher_id " +
            "JOIN accounts  a ON a.id = ve.account " +
            "WHERE v.user_id = #{userId}::uuid " +
            "  AND v.status = 'posted' " +
            "  AND v.deleted = 0 " +
            "  AND v.voucher_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY ve.account, a.account_type, a.name")
    List<Map<String, Object>> sumByAccount(@Param("userId") UUID userId,
                                           @Param("startDate") String startDate,
                                           @Param("endDate") String endDate);

    /**
     * 取某用户某年度所有科目的期初余额（年初余额）。
     * 用于资产负债表“年初余额”列。
     *
     * @return 每行含 accountId / debitBalance / creditBalance
     */
    @Select("SELECT account_id     AS account_id, " +
            "       debit_balance  AS debit_balance, " +
            "       credit_balance AS credit_balance " +
            "FROM account_opening_balances " +
            "WHERE user_id = #{userId}::uuid " +
            "  AND year = #{year}")
    List<Map<String, Object>> openingBalance(@Param("userId") UUID userId,
                                             @Param("year") int year);
}
