package com.macro.mall.demo.service;

import com.macro.mall.demo.dto.PmsBrandDto;
import com.macro.mall.model.PmsBrand;

import java.util.List;

/**
 * DemoServiceAPI
 * Created by macro on 2019/4/8.
 */
public interface DemoService {
    List<PmsBrand> listAllBrand();

    int createBrand(PmsBrandDto pmsBrandDto);

    int updateBrand(Long id, PmsBrandDto pmsBrandDto);

    int deleteBrand(Long id);

    List<PmsBrand> listBrand(int pageNum, int pageSize);

    PmsBrand getBrand(Long id);
}
