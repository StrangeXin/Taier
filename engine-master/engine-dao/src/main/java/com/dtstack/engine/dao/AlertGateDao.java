package com.dtstack.engine.dao;

import com.dtstack.engine.api.domain.po.AlertGatePO;
import com.dtstack.engine.api.domain.po.ClusterAlertPO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Date: 2020/6/16
 * Company: www.dtstack.com
 *
 * @author xiaochen
 */
public interface AlertGateDao {

    int update(AlertGatePO alertGatePO);

    int insert(AlertGatePO alertGatePO);

    int delete(Long gateId);

    List<AlertGatePO> list(AlertGatePO alertGatePO);

    AlertGatePO get(AlertGatePO alertGatePO);

    List<ClusterAlertPO> selectAlertByIds(@Param("alertGateSources") List<String> alertGateSources);

    List<ClusterAlertPO> selectDefaultAlert(@Param("alertTypes") List<Integer> alertTypes, @Param("isDefault") Integer isDefault);

    List<ClusterAlertPO> allGate();

}