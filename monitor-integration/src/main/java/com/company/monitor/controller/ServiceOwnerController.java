package com.company.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.common.BizException;
import com.company.monitor.common.Result;
import com.company.monitor.entity.ServiceOwner;
import com.company.monitor.mapper.ServiceOwnerMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "服务负责人映射")
@RestController
@RequestMapping("/api/v1/service-owners")
public class ServiceOwnerController {

    private final ServiceOwnerMapper serviceOwnerMapper;

    public ServiceOwnerController(ServiceOwnerMapper serviceOwnerMapper) {
        this.serviceOwnerMapper = serviceOwnerMapper;
    }

    @Operation(summary = "服务负责人列表")
    @GetMapping
    public Result<List<ServiceOwner>> list() {
        return Result.ok(serviceOwnerMapper.selectList(new QueryWrapper<ServiceOwner>().orderByDesc("id")));
    }

    @Operation(summary = "新增/更新服务负责人（按 serviceName 幂等）")
    @PostMapping
    public Result<ServiceOwner> save(@RequestBody ServiceOwner req) {
        if (req.getServiceName() == null || req.getServiceName().isBlank()) {
            throw new BizException("serviceName 不能为空");
        }
        ServiceOwner existing = serviceOwnerMapper.selectOne(new QueryWrapper<ServiceOwner>()
                .eq("service_name", req.getServiceName()).last("limit 1"));
        if (existing != null) {
            existing.setWecomUserids(req.getWecomUserids());
            existing.setEmailList(req.getEmailList());
            if (req.getEnabled() != null) {
                existing.setEnabled(req.getEnabled());
            }
            serviceOwnerMapper.updateById(existing);
            return Result.ok(existing);
        }
        if (req.getEnabled() == null) {
            req.setEnabled(1);
        }
        serviceOwnerMapper.insert(req);
        return Result.ok(req);
    }

    @Operation(summary = "删除服务负责人")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        serviceOwnerMapper.deleteById(id);
        return Result.ok();
    }
}
