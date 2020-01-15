package com.defvul.passets.api.bo.res;

import com.defvul.passets.api.vo.ApplicationVO;
import com.defvul.passets.api.vo.GeoIpVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@ApiModel(description = "主机资产列表")
public class HostBO {

    @ApiModelProperty(value = "ip")
    private String ip;

    @ApiModelProperty(value = "站点", notes = "HTTP类型数据")
    private String site;

    @ApiModelProperty(value = "是否内网", notes = "HTTP类型数据")
    private Boolean inner;

    @ApiModelProperty(value = "地理位置")
    private GeoIpVO geoIp;

    @ApiModelProperty(value = "操作系统")
    private List<ApplicationVO> apps;

    @ApiModelProperty(value = "主机端口信息")
    private List<HostListBO> hosts;

    @ApiModelProperty(value = "站点标题")
    private String title;

    @ApiModelProperty(value = "端口")
    private List<String> ports;

    @ApiModelProperty(value = "组件")
    private Set<String> assembly;

    @ApiModelProperty(value = "协议")
    private Set<String> services;

}