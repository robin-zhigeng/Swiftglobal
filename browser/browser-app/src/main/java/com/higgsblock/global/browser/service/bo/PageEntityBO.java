package com.higgsblock.global.browser.service.bo;

import lombok.Data;

import java.util.List;

/**
 * @author yangshenghong
 * @date 2018-05-23
 */
@Data
public class PageEntityBO<T> {
    /**
     * total count
     */
    private long total;

    /**
     * The record content of pages.
     */
    private List<T> items;

    public static Builder createBuilder() {
        return new Builder();
    }

    public static class Builder<T> {
        private long total;
        private List<T> items;

        public Builder<T> withTotal(long total) {
            this.total = total;
            return this;
        }

        public Builder<T> withItmes(List<T> items) {
            this.items = items;
            return this;
        }

        public PageEntityBO<T> builder() {
            PageEntityBO<T> entity = new PageEntityBO<>();
            entity.setItems(items);
            entity.setTotal(total);
            return entity;
        }
    }
}
