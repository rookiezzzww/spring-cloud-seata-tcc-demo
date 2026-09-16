package com.bite.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bite.storage.entity.StorageInfo;
import com.bite.storage.mapper.StorageMapper;
import com.bite.storage.service.StorageTccService;
import io.seata.rm.tcc.api.BusinessActionContext;
import io.seata.rm.tcc.api.BusinessActionContextParameter;
import io.seata.rm.tcc.api.LocalTCC;
import io.seata.rm.tcc.api.TwoPhaseBusinessAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@LocalTCC //表示实现了二阶段提交的本地TCC接口
public class StorageTccServiceImpl implements StorageTccService {
    @Autowired
    private StorageMapper storageMapper;

    /**
     * useTCCFence = true表示使用TCC自带的空回滚，业务悬挂以及幂等处理
     */
    @Override
    @TwoPhaseBusinessAction(name = "StorageTccDeduct", commitMethod = "confirm", rollbackMethod = "cancel", useTCCFence = true)
    public void prepare(@BusinessActionContextParameter(value = "commodityCode") String commodityCode, @BusinessActionContextParameter(value = "count") Integer count) {
        log.info("一阶段try");
        try {
            UpdateWrapper<StorageInfo> updateWrapper = new UpdateWrapper<>();
            updateWrapper.lambda().setSql("count = count - "+ count)
                    .setSql("freeze_count = freeze_count + "+ count)
                    .eq(StorageInfo::getCommodityCode, commodityCode);
            storageMapper.update(updateWrapper);
//            int a = 10/0;
        } catch (Exception e) {
            log.error("扣减库存失败, e:", e);
            throw new RuntimeException("扣减库存失败!", e);
        }
    }

    @Override
    public boolean confirm(BusinessActionContext context) {
        log.info("⼆阶段Confirm....");
        //真实扣除冻结库存
        Integer count = (Integer) context.getActionContext("count");
        String commodityCode = (String) context.getActionContext("commodityCode");

        UpdateWrapper<StorageInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().setSql("freeze_count = freeze_count - "+ count)
                .eq(StorageInfo::getCommodityCode, commodityCode);
        int result = storageMapper.update(updateWrapper);
        return result==1;
    }

    @Override
    public boolean cancel(BusinessActionContext context) {
        log.info("⼆阶段Cancel....");
        //恢复可⽤库存, 清除冻结库存
        Integer count = (Integer) context.getActionContext("count");
        String commodityCode = (String) context.getActionContext("commodityCode");

        UpdateWrapper<StorageInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.lambda().setSql("count = count + "+ count)
                .setSql("freeze_count = freeze_count - "+ count)
                .eq(StorageInfo::getCommodityCode, commodityCode);
        int result = storageMapper.update(updateWrapper);
        return result == 1;
    }
}
