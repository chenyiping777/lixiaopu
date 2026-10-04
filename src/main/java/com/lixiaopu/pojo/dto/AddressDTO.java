package com.lixiaopu.pojo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class AddressDTO {
    private String id;
    @JsonAlias("receiver")
    private String receiverName;
    @JsonAlias("phone")
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private Boolean isDefault;
}
