package cn.zlianpay.common.system.controller;

import cn.zlianpay.common.core.web.BaseController;
import cn.zlianpay.common.core.web.JsonResult;
import cn.zlianpay.common.system.entity.LoginRecord;
import cn.zlianpay.common.system.entity.Menu;
import cn.zlianpay.common.system.service.LoginRecordService;
import cn.zlianpay.common.system.service.MenuService;
import cn.zlianpay.settings.entity.Pays;
import cn.zlianpay.settings.service.PaysService;
import cn.zlianpay.website.entity.Website;
import cn.zlianpay.website.service.WebsiteService;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wf.captcha.utils.CaptchaUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页、登录、验证码等
 * Created by Panyoujie on 2018-12-24 16:10
 */
@Controller
public class MainController extends BaseController implements ErrorController {

    @Autowired
    private MenuService menuService;

    @Autowired
    private LoginRecordService loginRecordService;

    @Autowired
    private WebsiteService websiteService;

    @Autowired
    private PaysService paysService;

    /**
     * 用户登录
     */
    @ResponseBody
    @PostMapping("/login")
    public JsonResult login(String username, String password, String code, Boolean remember, HttpServletRequest request) {
        if (username == null || username.trim().isEmpty()) return JsonResult.error("请输入账号");
        if (!CaptchaUtil.ver(code, request)) {
            CaptchaUtil.clear(request);  // 清除session中的验证码
            loginRecordService.saveAsync(username, LoginRecord.TYPE_ERROR, "验证码错误", request);
            return JsonResult.error("验证码不正确");
        }
        try {
            if (remember == null) remember = false;
            SecurityUtils.getSubject().login(new UsernamePasswordToken(username, password, remember));
            loginRecordService.saveAsync(username, request);
            return JsonResult.ok("登录成功");
        } catch (IncorrectCredentialsException ice) {
            loginRecordService.saveAsync(username, LoginRecord.TYPE_ERROR, "密码错误", request);
            return JsonResult.error("密码错误");
        } catch (UnknownAccountException uae) {
            loginRecordService.saveAsync(username, LoginRecord.TYPE_ERROR, "账号不存在", request);
            return JsonResult.error("账号不存在");
        } catch (LockedAccountException e) {
            loginRecordService.saveAsync(username, LoginRecord.TYPE_ERROR, "账号被锁定", request);
            return JsonResult.error("账号被锁定");
        } catch (ExcessiveAttemptsException eae) {
            loginRecordService.saveAsync(username, LoginRecord.TYPE_ERROR, "操作频繁", request);
            return JsonResult.error("操作频繁，请稍后再试");
        }
    }

    /**
     * 登录页
     */
    @GetMapping("/login")
    public String login(Model model) {

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);

        if (getLoginUser() != null) return "redirect:index";
        return "login.html";
    }

    /**
     * 主页
     */
    @RequestMapping("/admin")
    public String index(Model model) {

        Pays pays = paysService.getOne(new QueryWrapper<Pays>().eq("driver", "paypal"));
        if (ObjectUtils.isEmpty(pays)) {
            Pays pays1 = new Pays();
            pays1.setName("Paypal");
            pays1.setDriver("paypal");

            Map<String,String> map = new HashMap<>();
            map.put("clientId", "xxx");
            map.put("clientSecret", "xxx");
            map.put("return_url", "xxx");
            String jsonString = JSON.toJSONString(map);
            pays1.setConfig(jsonString);
            pays1.setComment("Paypal 境外支付（默认美元交易）");
            pays1.setEnabled(0);
            pays1.setCreatedAt(new Date());
            pays1.setUpdatedAt(new Date());
            paysService.save(pays1);
        }

        Website website = websiteService.getById(1);
        model.addAttribute("website", website);

        // 左侧菜单
        List<Menu> menus = menuService.getUserMenu(getLoginUserId(), Menu.TYPE_MENU);
        model.addAttribute("menus", menuService.toMenuTree(menus, 0));
        return "main.html";
    }

    /**
     * 图形验证码
     */
    @RequestMapping("/assets/captcha")
    public void captcha(HttpServletRequest request, HttpServletResponse response) {
        try {
            CaptchaUtil.out(5, request, response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 主页弹窗页面
     */
    @RequestMapping("/tpl/{name}")
    public String tpl(@PathVariable("name") String name) {
        return "index/" + name + ".html";
    }

    /**
     * 错误页
     */
    @RequestMapping("/error")
    public String error() {
        return "error/404.html";
    }

    @Override
    public String getErrorPath() {
        return "/error";
    }

}
