package com.hify.common.web;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PageResult<T> extends Result<List<T>> {

    private long total;
    private int page;
    private int size;

    public static <T> PageResult<T> ok(List<T> data, long total, int page, int size) {
        PageResult<T> r = new PageResult<>();
        r.setCode(200);
        r.setMessage("ok");
        r.setData(data);
        r.total = total;
        r.page = page;
        r.size = size;
        return r;
    }
}
