package com.hify.demo.service;

import com.hify.common.web.PageResult;
import com.hify.demo.dto.DemoItemCreateReq;
import com.hify.demo.dto.DemoItemResp;
import com.hify.demo.dto.DemoItemUpdateReq;

public interface DemoItemService {

    DemoItemResp create(DemoItemCreateReq req);

    DemoItemResp update(Long id, DemoItemUpdateReq req);

    void delete(Long id);

    DemoItemResp get(Long id);

    PageResult<DemoItemResp> page(int page, int size);
}
