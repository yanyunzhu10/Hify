package com.hify.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
