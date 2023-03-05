package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.heima.apis.article.IArticleClient;
import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author WangNing
 */
@Slf4j
@Service
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {

    @Autowired
    private WmNewsMapper wmNewsMapper;

    @Autowired
    private GreenTextScan greenTextScan;

    @Autowired
    private GreenImageScan greenImageScan;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private IArticleClient articleClient;

    @Autowired
    private WmChannelMapper wmChannelMapper;

    @Autowired
    private WmUserMapper wmUserMapper;

    /**
     * 自媒体文章审核
     *
     * @param id 自媒体文章id
     */
    @Override
    public void autoScanWmNews(Integer id) {
        // 查询自媒体文章
        WmNews wmNews = wmNewsMapper.selectById(id);
        if (wmNews == null) {
            throw new RuntimeException("WmNewsAutoScanServiceImpl-文章不存在");
        }

        // 处于待审核状态的文章才被提取审核
        if (wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())) {
            // 从文本中提取纯文本内容和图片
            Map<String, Object> textAndImages = handleTextAndImages(wmNews);

            // 审核文本内容 -- 阿里云接口
            boolean isTextScan = handleTextScan((String) textAndImages.get("content"), wmNews);
            if (!isTextScan) {
                return;
            }

            // 审核图片 -- 阿里云接口
            boolean isImageScan = handleImageScan((List<String>) textAndImages.get("images"), wmNews);
            if (!isImageScan) {
                return;
            }

            // 审核通过，保存app端的相关文章数据
            ResponseResult responseResult = saveArticle(wmNews);
            if (!responseResult.getCode().equals(200)) {
                throw new RuntimeException("WmNewsAutoScanServiceImpl-文章审核，保存app端相关文章数据失败");
            }
            // 回填article_id
            wmNews.setArticleId((Long) responseResult.getData());
            updateWmNews(wmNews, (short) 9,"审核成功");
        }
    }

    /**
     * 保存app端的相关文章数据
     *
     * @param wmNews
     * @return
     */
    private ResponseResult saveArticle(WmNews wmNews) {

        ArticleDto dto = new ArticleDto();
        // 属性拷贝
        BeanUtils.copyProperties(wmNews, dto);
        // 文章的布局
        dto.setLayout(wmNews.getType());
        // 频道
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        if (wmChannel != null) {
            dto.setChannelName(wmChannel.getName());
        }
        // 作者
        dto.setAuthorId(wmNews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        if (wmUser != null) {
            dto.setAuthorName(wmUser.getName());
        }
        // 设置文章id
        if (wmNews.getArticleId() != null) {
            dto.setId(wmNews.getArticleId());
        }
        dto.setCreatedTime(new Date());

        ResponseResult responseResult = articleClient.saveArticle(dto);
        return responseResult;
    }

    /**
     * 审核图片内容 -- 阿里云接口
     *
     * @param images
     * @param wmNews
     * @return
     */
    private boolean handleImageScan(List<String> images, WmNews wmNews) {
        /* 是否审核通过 */
        boolean flag = true;
        /* 图片字符数组列表 */
        List<byte[]> imageList = new ArrayList<>();

        if (images == null || images.size() == 0) {
            return flag;
        }

        // 图片去重 and 下载图片到minIO中
        images = images.stream().distinct().collect(Collectors.toList());
        for (String image : images) {
            byte[] bytes = fileStorageService.downLoadFile(image);
            imageList.add(bytes);
        }

        // 审核图片
        try {
            Map map = greenImageScan.imageScan(imageList);
            if (map != null) {
                // 审核失败
                if ("block".equals(map.get("suggestion"))) {
                    updateWmNews(wmNews, (short) 2, "当前文章中存在违规内容");
                    flag = false;
                }

                // 审核不确定，需要人工审核
                if ("review".equals(map.get("suggestion"))) {
                    updateWmNews(wmNews, (short) 3, "当前文章中存在不确定内容");
                    flag = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
        }

        return flag;
    }

    /**
     * 审核文本内容 -- 阿里云接口
     *
     * @param content
     * @param wmNews
     * @return
     */
    private boolean handleTextScan(String content, WmNews wmNews) {

        /* 是否审核通过 */
        boolean flag = true;
        /* title + content 都需要审核 */
        String text = wmNews.getTitle() + content;

        if (text.length() == 0) {
            return flag;
        }

        try {
            Map map = greenTextScan.greeTextScan(text);
            if (map != null) {
                // 审核失败
                if ("block".equals(map.get("suggestion"))) {
                    updateWmNews(wmNews, (short) 2, "当前文章中存在违规内容");
                    flag = false;
                }

                // 审核不确定，需要人工审核
                if ("review".equals(map.get("suggestion"))) {
                    updateWmNews(wmNews, (short) 3, "当前文章中存在不确定内容");
                    flag = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
        }

        return flag;
    }

    /**
     * 修改文章内容 -- 审核 status and reason
     *
     * @param wmNews
     * @param status
     * @param reason
     */
    private void updateWmNews(WmNews wmNews, short status, String reason) {
        wmNews.setStatus(status);
        wmNews.setReason(reason);
        wmNewsMapper.updateById(wmNews);
    }

    /**
     * 从自媒体文章的content中提取文本内容和图片 and 提取文章的封面图片
     *
     * @param wmNews
     * @return
     */
    private Map<String, Object> handleTextAndImages(WmNews wmNews) {

        /* 纯文本内容 */
        StringBuilder textStr = new StringBuilder();
        /* 图片list */
        List<String> images = new ArrayList<>();

        // 从自媒体文章的内容中提取文本内容和图片
        if (StringUtils.isNotBlank(wmNews.getContent())) {
            List<Map> maps = JSONArray.parseArray(wmNews.getContent(), Map.class);
            for (Map map : maps) {
                if ("text".equals(map.get("type"))) {
                    textStr.append(map.get("value"));
                }
                if ("image".equals(map.get("type"))) {
                    images.add((String) map.get("value"));
                }
            }
        }

        // 提取文章的封面图片
        if (StringUtils.isNotBlank(wmNews.getImages())) {
            String[] covers = wmNews.getImages().split(",");
            images.addAll(Arrays.asList(covers));
        }

        // 封装返回结果
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("content", textStr.toString());
        resultMap.put("images", images);

        return resultMap;
    }
}
