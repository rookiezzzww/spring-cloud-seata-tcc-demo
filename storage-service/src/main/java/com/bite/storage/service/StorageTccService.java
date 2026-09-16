package com.bite.storage.service;

import io.seata.rm.tcc.api.BusinessActionContext;

public interface StorageTccService {
    /**
     * 扣减库存
     */
    void prepare(String commodityCode, Integer count);
    boolean confirm(BusinessActionContext context);
    boolean cancel(BusinessActionContext context);
}
