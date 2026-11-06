package com.macro.mall.search.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;

/**
 * 검색 상품의 속성 정보
 * Created by macro on 2018/6/27.
 */
@Data
@EqualsAndHashCode
public class EsProductAttributeValue implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long productAttributeId;
    //속성값
    @Field(type = FieldType.Keyword)
    private String value;
    //속성 파라미터:0->규격;1->파라미터
    private Integer type;
    //속성명
    @Field(type=FieldType.Keyword)
    private String name;
}
