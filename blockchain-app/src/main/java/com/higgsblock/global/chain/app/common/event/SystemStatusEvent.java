package com.higgsblock.global.chain.app.common.event;

import com.alibaba.fastjson.annotation.JSONType;
import com.higgsblock.global.chain.app.common.SystemStatus;
import com.higgsblock.global.chain.common.entity.BaseSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yuguojia
 * @date 2018/04/03
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@JSONType(includes = {"systemStatus"})
public class SystemStatusEvent extends BaseSerializer {
    private SystemStatus systemStatus;
}