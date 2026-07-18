package com.badminton.dto.response;

import com.github.pagehelper.PageInfo;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 分页查询统一返回结构
 */
@Getter
@Setter
public class PageResult<T> {
    private List<T> list;       // 当前页数据
    private long total;         // 总记录数
    private int pageNum;        // 当前页码
    private int pageSize;       // 每页条数
    private int pages;          // 总页数

    /** 原始列表（PageHelper 返回的 Page 对象），直接提取分页信息 */
    public static <T> PageResult<T> of(List<T> rawList) {
        PageInfo<T> info = new PageInfo<>(rawList);
        PageResult<T> r = new PageResult<>();
        r.list = info.getList();
        r.total = info.getTotal();
        r.pageNum = info.getPageNum();
        r.pageSize = info.getPageSize();
        r.pages = info.getPages();
        return r;
    }

    /** 从 rawList 提取分页信息，dataList 作为当前页数据（用于 stream 转换后丢失 Page 类型的场景） */
    public static <T> PageResult<T> of(List<?> rawList, List<T> dataList) {
        PageInfo<?> info = new PageInfo<>(rawList);
        PageResult<T> r = new PageResult<>();
        r.list = dataList;
        r.total = info.getTotal();
        r.pageNum = info.getPageNum();
        r.pageSize = info.getPageSize();
        r.pages = info.getPages();
        return r;
    }
}
