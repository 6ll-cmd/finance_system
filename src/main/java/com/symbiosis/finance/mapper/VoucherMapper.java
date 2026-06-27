package com.symbiosis.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.symbiosis.finance.entity.Voucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface VoucherMapper extends BaseMapper<Voucher> {

    @Select("SELECT id FROM vouchers WHERE id LIKE 'VCH-' || #{year} || '-%' ORDER BY id DESC LIMIT 1")
    String findLastIdByYear(int year);

    @Select("SELECT status, COUNT(*) AS count, ROUND(COALESCE(SUM(total_amount),0),2) AS totalAmount " +
            "FROM vouchers WHERE user_id=#{uid} AND deleted=0 GROUP BY status")
    List<Map<String, Object>> statusReport(UUID uid);

    @Select("SELECT COUNT(*) FROM vouchers WHERE user_id=#{uid} AND deleted=0")
    Long countActive(UUID uid);

    @Select("SELECT ROUND(COALESCE(SUM(total_amount),0),2) FROM vouchers WHERE user_id=#{uid} AND deleted=0")
    Double sumActiveAmount(UUID uid);

    @Select("SELECT ROUND(COALESCE(SUM(total_amount),0),2) FROM vouchers WHERE user_id=#{uid} AND deleted=0 AND status='posted'")
    Double sumPostedAmount(UUID uid);
}
