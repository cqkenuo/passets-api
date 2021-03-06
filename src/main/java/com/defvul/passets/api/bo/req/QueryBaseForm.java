package com.defvul.passets.api.bo.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 说明:
 * 时间: 2019/11/11 10:33
 *
 * @author wimas
 */
@Data
@ApiModel(description = "查询表单")
public class QueryBaseForm {

    @ApiModelProperty(value = "开始时间")
    private Date start;

    @ApiModelProperty(value = "结束时间")
    private Date end;

    @ApiModelProperty(value = "IP")
    private String ip;

    @ApiModelProperty(value = "URL")
    private String url;

    @ApiModelProperty(value = "站点")
    private String site;

    @ApiModelProperty(value = "端口")
    private String port;

    @ApiModelProperty(value = "指纹名称")
    private String finger;

    @ApiModelProperty(value = "是否内网,1:内网，2：外网")
    private Integer inner;

    @ApiModelProperty(value = "类型", notes = "HTTP, HTTPS, TCP")
    private List<String> pro;

    @ApiModelProperty(value = "分类ID")
    @SerializedName("category_id")
    @JsonProperty("category_id")
    private List<Long> categoryId;

    @ApiModelProperty(value = "国家")
    private String country;

    @ApiModelProperty(value = "操作系统")
    private String os;

    @ApiModelProperty(value = "标题")
    private String title;

    @ApiModelProperty(value = "来源")
    private String tag;
}

