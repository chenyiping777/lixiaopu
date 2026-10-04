package com.lixiaopu.controller.user;

import com.lixiaopu.common.result.Result;
import com.lixiaopu.pojo.dto.AddressDTO;
import com.lixiaopu.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/address")
public class AddressController {

    @Autowired
    private  AddressService addressService;

    /**
     * 查询地址列表
     *
     * @return
     */
    @GetMapping("/list")
    public Result getAddressList() {
        return addressService.getAddressList();

    }

    /**
     * 新增地址
     * @param addressDTO
     * @return
     */
    @PostMapping("/add")
    public Result insertAddress(@RequestBody @Valid AddressDTO addressDTO) {
        return addressService.insertAddress(addressDTO);

    }

    /**
     * 修改地址
     *
     * @param addressDTO
     * @return
     */
    @PutMapping("/update")
    public Result updateAddress(@RequestBody @Valid AddressDTO addressDTO) {
        return addressService.updateAddress(addressDTO);

    }

    /**
     * 删除地址
     *
     * @param id
     * @return
     */
    @DeleteMapping("/delete")
    public Result deleteAddress(@RequestParam Long id) {
        return addressService.deleteAddress(id);

    }
}
