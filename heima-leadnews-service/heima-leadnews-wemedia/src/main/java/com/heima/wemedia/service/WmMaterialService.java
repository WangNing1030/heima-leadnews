package com.heima.wemedia.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import org.springframework.web.multipart.MultipartFile;

public interface WmMaterialService extends IService<WmMaterial> {

    /**
     * 素材图片上传
     *
     * @param multipartFile
     * @return
     */
    ResponseResult uploadPicture(MultipartFile multipartFile);

    /**
     * 素材列表查询
     *
     * @param dto
     * @return
     */
    ResponseResult findList(WmMaterialDto dto);

    /**
     * 素材图片删除
     *
     * @param id 图片id
     * @return
     */
    ResponseResult deletePicture(Integer id);

    /**
     * 收藏素材图片
     *
     * @param id
     * @return
     */
    ResponseResult collect(Integer id);

    /**
     * 取消收藏素材图片
     *
     * @param id
     * @return
     */
    ResponseResult cancelCollect(Integer id);
}