package com.lixiaopu.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageVO<T> {

    private Long total;//总条数
    private Long pages;//总页数
    private List<T> list;

}
