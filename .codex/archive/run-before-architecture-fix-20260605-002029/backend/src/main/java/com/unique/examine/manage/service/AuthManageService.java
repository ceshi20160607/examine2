package com.unique.examine.manage.service;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.vo.AuthTokenVO;
import com.unique.examine.manage.vo.UserVO;

public interface AuthManageService {
    AuthTokenVO register(AuthRegisterBO bo);
    AuthTokenVO login(AuthLoginBO bo);
    AuthTokenVO refresh();
    UserVO me();
}
