package com.hify.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DemoItemUpdateReq {

    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    @NotNull(message = "status 不能为空")
    private Integer status;
}
