package com.symbiosis.finance.service;

public final class AiConstants {

    private AiConstants() {}

    public static final String OCR_PROMPT = """
        请识别这张发票图片，尽量提取发票上的全部结构化信息，并只返回 JSON，不要返回解释文字。
        字段名必须使用下面的英文 key：
        {
          "type": "发票类型，例如 电子发票（普通发票）",
          "number": "发票号码",
          "date": "开票日期，格式 YYYY-MM-DD",
          "buyer": "购买方名称",
          "buyerTaxNo": "购买方统一社会信用代码/纳税人识别号",
          "buyerAddressPhone": "购买方地址、电话，没有则空字符串",
          "buyerBankAccount": "购买方开户行及账号，没有则空字符串",
          "seller": "销售方名称",
          "sellerTaxNo": "销售方统一社会信用代码/纳税人识别号",
          "sellerAddressPhone": "销售方地址、电话，没有则空字符串",
          "sellerBankAccount": "销售方开户行及账号，没有则空字符串",
          "itemName": "项目名称/货物或应税劳务、服务名称，保留税收分类前缀，例如 *修理修配服务*修理修配服务",
          "itemSpec": "规格型号，没有则空字符串",
          "itemUnit": "单位，例如 项",
          "itemQuantity": "数量，数字",
          "itemUnitPrice": "单价，数字",
          "amount": "不含税金额，数字",
          "taxRate": "税率数字，例如 1 表示 1%",
          "taxAmount": "税额，数字",
          "totalAmountCn": "价税合计大写",
          "totalAmount": "价税合计小写，数字",
          "notes": "备注",
          "category": "类别，只能是 service/travel/catering/office/utility/logistics/rental/other"
        }
        无法识别的文本字段返回空字符串，无法识别的数字字段返回 0。金额不要把单价误认为金额，优先使用“合计”和“价税合计（小写）”栏。
        """;

    public static final String AI_SYSTEM_PROMPT = """
        你是发票管家 AI 助手，帮助用户高效管理财务。
        你可以录入发票、查询统计、分析数据、建议凭证分录、回答发票税务和会计相关问题。
        当用户要求执行操作时，返回 JSON：{"action":"操作名","data":{...},"reply":"回复文字"}。
        当用户只是聊天时，直接返回中文回复，不超过 200 字。
        """;
}
