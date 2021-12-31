/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.engine.mapper;

import com.dtstack.engine.domain.ScheduleJobJob;
import com.dtstack.engine.dto.ScheduleJobJobTaskDTO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * company: www.dtstack.com
 * author: toutian
 * create: 2019/10/22
 */
public interface ScheduleJobJobDao {

    List<ScheduleJobJob> listByJobKey(@Param("jobKey") String jobKey);

    List<ScheduleJobJob> listByJobKeys(@Param("list") List<String> jobKeys);

    List<ScheduleJobJob> listByParentJobKey(@Param("jobKey") String jobKey);

    Integer insert(ScheduleJobJob scheduleJobJob);

    Integer batchInsert(Collection batchJobJobs);

    Integer update(ScheduleJobJob scheduleJobJob);

    List<ScheduleJobJob> listSelfDependency(@Param("pjobKey") String pjobKey);

    List<ScheduleJobJob> listByParentJobKeys(@Param("list") List<String> list);

    List<ScheduleJobJobTaskDTO> listByParentJobKeysWithOutSelfTask(@Param("jobKeyList") List<String> jobKeyList);

    List<ScheduleJobJobTaskDTO> listByJobKeysWithOutSelfTask(@Param("jobKeyList") List<String> jobKeys);

    void deleteByJobKey(@Param("jobKeyList") List<String> jobKeyList);
}