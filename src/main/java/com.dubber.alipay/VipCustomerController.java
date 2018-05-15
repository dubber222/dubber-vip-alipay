package com.dubber.alipay;


import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConstants;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.google.common.collect.Maps;
import com.purang.web.export.ExportConstant;
import com.purang.web.payment.PropFile;
import com.purang.web.payment.alipay.AlipayAPIClientFactory;
import com.purang.web.proxy.WebProxy;
import com.purang.web.template.TemplateController;
import com.purang.web.template.art.MemberServiceInterfaceController;
import com.purang.web.util.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
public class VipCustomerController extends TemplateController {
    private static final Logger logger = Logger.getLogger(VipCustomerController.class);

    private static Map vipTypeMap = new HashMap();

    static{
        vipTypeMap.put("1","一级");
        vipTypeMap.put("2","二级");
        vipTypeMap.put("3","三级");
    }

    /**
     * vip活动： 101：517司庆活动
     */
    private static final String VIP_DISCOUNT_TYPE_517 = "101";

    /**
     * 支付页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toCustomerBankCardsList.htm")
    public ModelAndView toCustomerResizerList(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        String orderNo = request.getParameter("orderNo");
        String totalMoney = request.getParameter("totalMoney");
        String isVip = request.getParameter("isVip");
        String type = request.getParameter("type");
        String statusType = request.getParameter("statusType");
        Map<String, String> accountInfoMap = (Map<String, String>) request.getSession().getAttribute("accountInfoParams");
        String userId = accountInfoMap.get("userId");
        String customerType = accountInfoMap.get("customerType");
        String customerId = accountInfoMap.get("customerId");
        String customerName = accountInfoMap.get("customerName");
        String userName = accountInfoMap.get("userName");
        mv.addObject("orderNo", orderNo);
        mv.addObject("totalMoney", totalMoney);
        mv.addObject("isVip", isVip);
        mv.addObject("type", type);
        mv.addObject("statusType", statusType);
        mv.addObject("userId", userId);
        mv.addObject("customerType", customerType);
        mv.addObject("customerId", customerId);
        mv.addObject("customerName", customerName);
        mv.addObject("userName", userName);
        mv.setViewName("crm/vip/customerBankCards");
        return mv;
    }


    /**
     * 初始化二维码页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/qrCodePay.htm")
    public static ModelAndView toQrCodeList(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        String orderNo = request.getParameter("orderNo");
        String totalMoney = request.getParameter("totalMoney");
        String isVip = request.getParameter("isVip");
        String type = request.getParameter("type");
        Map<String, String> accountInfoMap = (Map<String, String>) request.getSession().getAttribute("accountInfoParams");
        String userId = accountInfoMap.get("userId");
        String customerType = accountInfoMap.get("customerType");
        String customerId = accountInfoMap.get("customerId");
        String customerName = accountInfoMap.get("customerName");
        String userName = accountInfoMap.get("userName");
        String statusType = request.getParameter("statusType");

        mv.addObject("orderNo", orderNo);
        mv.addObject("totalMoney", totalMoney);
        mv.addObject("isVip", isVip);
        mv.addObject("type", type);
        mv.addObject("userId", userId);
        mv.addObject("customerType", customerType);
        mv.addObject("customerId", customerId);
        mv.addObject("customerName", customerName);
        mv.addObject("userName", userName);
        mv.addObject("statusType", statusType);

        mv.setViewName("crm/vip/qrCodePay");
        return mv;
    }


    /**
     * 获取扫码支付的二维码
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "/customer/pay/alipay/getQrcode.htm")
    public void getAlipayQrcode(HttpServletRequest request, HttpServletResponse response) {
        JSONObject responseResult = new JSONObject();
        String orderNo = request.getParameter("orderNo");
        if (StringUtils.isBlank(orderNo)) {
            responseResult.put("success", false);
            responseResult.put("responseCode", 400);
            responseResult.put("errorMsg", "缺少订单编号");
            outputResult(response, responseResult);
            return;
        }
        Map<String, String> loginSession = SessionUtils.getSession(request, SessionUtils.BUSINESS_TYPE_BROKER);
        JSONObject orderResult = getPostRequestResult(loginSession, "/ec/vip/queryVipOrderInfo.html", "{'orderNo':'" + orderNo + "'}");
        boolean isSuccess = orderResult.optBoolean("success");
        if (!isSuccess) {
            responseResult.put("success", false);
            responseResult.put("responseCode", 500);
            responseResult.put("errorMsg", "获取订单号[" + orderNo + "]数据出现异常");
            outputResult(response, responseResult);
            return;
        }
        JSONObject orderJson = orderResult.optJSONObject("data");
        int pbqResultCode = orderJson.optInt("resultCode");
        if (pbqResultCode != 200) {
            responseResult.put("success", false);
            responseResult.put("responseCode", 500);
            responseResult.put("errorMsg", "获取订单号[" + orderNo + "]数据出现异常");
            outputResult(response, responseResult);
            return;
        }
        JSONObject pbqResultData = orderJson.optJSONObject("resultData");
        double paidMoney = pbqResultData.optDouble("totalMoney");
        String vipType = pbqResultData.optString("vipType");
        SimpleDateFormat expireFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        String expireTime = expireFormatter.format(calendar.getTime());
        JSONObject inputJson = new JSONObject();
        inputJson.put("out_trade_no", orderNo);
        inputJson.put("total_amount", paidMoney);
        inputJson.put("subject", vipType);
        inputJson.put("time_expire", expireTime);
        AlipayClient alipayClient = AlipayAPIClientFactory.getVipAlipayClient();
        AlipayTradePrecreateRequest precreateRequest = new AlipayTradePrecreateRequest();
        precreateRequest.setBizContent(inputJson.toString());
        precreateRequest.setNotifyUrl(PropFile.getProps("app.notify.url.9999"));
        logger.info("--------------" + PropFile.getProps("app.notify.url.9999"));
//				request.putOtherTextParam("ws_service_url", "http://unitradeprod.t15032aqcn.alipay.net:8080");
        try {
            // 使用SDK，调用交易下单接口
            AlipayTradePrecreateResponse precreateResponse = alipayClient.execute(precreateRequest);
            if (null != precreateResponse && precreateResponse.isSuccess()) {
                if (precreateResponse.getCode().equals("10000")) {
                    JSONObject data = new JSONObject();
                    data.put("outTradeNo", precreateResponse.getOutTradeNo());
                    data.put("qrCode", precreateResponse.getQrCode());
                    responseResult.put("success", true);
                    responseResult.put("responseCode", 200);
                    responseResult.put("data", data);
                    responseResult.put("errorMsg", "");
                    outputResult(response, responseResult);
                    return;
                } else {
                    //打印错误码
                    responseResult.put("success", false);
                    responseResult.put("responseCode", 500);
                    responseResult.put("data", new JSONObject());
                    responseResult.put("errorMsg", precreateResponse.getMsg() + "->" + precreateResponse.getSubMsg());
                    outputResult(response, responseResult);
                    return;
                }
            } else {
                responseResult.put("success", false);
                responseResult.put("responseCode", 500);
                responseResult.put("errorMsg", precreateResponse.getMsg() + "->" + precreateResponse.getSubMsg());
                outputResult(response, responseResult);
                return;
            }
        } catch (AlipayApiException e) {
            logger.error(e.getMessage(), e);
            responseResult.put("success", false);
            responseResult.put("responseCode", 500);
            responseResult.put("errorMsg", "生成二维码出现异常");
            outputResult(response, responseResult);
            return;
        }


    }


    private void outputResult(HttpServletResponse response, JSONObject responseResult) {
        outputResult(response, responseResult.toString());
    }

    private void outputResult(HttpServletResponse response, String responseResult) {
        PrintWriter out = null;
        try {
            logger.info(responseResult.toString());
            response.setCharacterEncoding("UTF-8");
//			response.setContentType("application/text;charset=UTF-8");
            out = response.getWriter();
            out.print(responseResult.toString());
            out.flush();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }


    /**
     * 接收支付宝通知
     *
     * @param request
     * @param response
     */
    //功能环境-https://webproxytest2.purang.com/customer/vip/order/receiveNotify.htm
    //UAT环境-https://webproxy.purang.com/customer/vip/order/receiveNotify.htm
    @RequestMapping(value = "/customer/vip/order/receiveNotify.htm")
    public void receiveCustomerVipOrderNotify(HttpServletRequest request, HttpServletResponse response) {
        try {
            Map<String, String> params = getRequestParams(request);
            String orderNo = request.getParameter("out_trade_no");
            logger.info("receive Notify from alipay for order[" + orderNo + "],request param:" + params.toString());
            //验签
            boolean isPassVerify = verifySignForRequest(params);
            if (!isPassVerify) {
                logger.info("fail to verify sign for orderNo[" + orderNo + "]");
                outputResult(response, "fail to verify sign");
                return;
            }
            logger.info("succeed in verifying sign for orderNo[" + orderNo + "]");
            //解析支付宝发送的支付结果
            AlipaySyncNotify alipaySyncNotify = toAlipaySyncNotify(params);
            //处理通知
            String tradeStatus = alipaySyncNotify.getTradeStatus();
            if (StringUtils.equalsIgnoreCase("TRADE_CLOSED", tradeStatus)) {
                logger.info("trade is TRADE_CLOSED for orderNo[" + orderNo + "],ignore this notify.");
                outputResult(response, "success");
                return;
            } else if (StringUtils.equalsIgnoreCase("WAIT_BUYER_PAY", tradeStatus)) {
                logger.info("trade is WAIT_BUYER_PAY for orderNo[" + orderNo + "],ignore this notify.");
                outputResult(response, "success");
                return;
            }

            Map<String, String> loginSession = mockRequestSession(request);

            //写入通知表
            JSONObject saveNotifyParam = new JSONObject();
            saveNotifyParam.put("action", "saveAlipaySyncNotify");
            saveNotifyParam.put("alipaySyncNotify", JSONObject.fromObject(alipaySyncNotify));
            JSONObject saveNotifyResult = postRequestWrapper(loginSession, "/village/shop/doAction.html", saveNotifyParam);
            boolean isSuccessForSaveNotifyResult = saveNotifyResult.optBoolean("success");
            if (isSuccessForSaveNotifyResult) {
                JSONObject saveNotifyPbqResult = saveNotifyResult.optJSONObject("data");
                int pbqResultCode = saveNotifyPbqResult.optInt("resultCode");
                if (pbqResultCode != 200) {
                    logger.error("save alipay SyncNotify error:" + saveNotifyPbqResult.toString());
                }
            } else {
                logger.error("save alipay SyncNotify error:" + saveNotifyResult.toString());
            }
            //校验订单状态是否已经改变
            if (isHasUpdateOrderStatus(loginSession, orderNo)) {
                outputResult(response, "success");
                return;
            }
            //更新订单状态，插入会员表
            JSONObject saveOrderParam = new JSONObject();
            saveOrderParam.put("orderNo", orderNo);
            JSONObject updateOrderResult = postRequestWrapper(loginSession, "/ec/vip/saveVipCustomer.html", saveOrderParam);
            //相关账号添加角色
            JSONObject updateUserRoleResult = postRequestWrapper(loginSession, "/ec/vip/updateVipUserRole.html", saveOrderParam);
            int resultCode = updateOrderResult.getJSONObject("data").optInt("resultCode", 0);
            if (resultCode == 200) {
                logger.info("succeed in handling updateOrderResult Result:" + updateOrderResult.getJSONObject("data").toString());
                JSONObject resultData = updateOrderResult.getJSONObject("data").optJSONObject("resultData");
                //送花流程
                String userIds = resultData.optString("userIds");//当前客户所有正式账号
                String amount = resultData.optString("buyNum");
                String orderNoStr = resultData.optString("orderNo");
                String endTime = resultData.optString("endTime");
                String startTime = resultData.optString("startTime");
                String userId = resultData.optString("userId");//当前申请账号
                String activityCode = "9999";
                loginSession.put("userId", userId);
                String vipType = resultData.optString("vipType");
                MemberServiceInterfaceController memberServiceInterfaceController = new MemberServiceInterfaceController();
                memberServiceInterfaceController.fourNineMemberActivity(loginSession,amount, orderNoStr, endTime, activityCode, userIds,startTime,vipType);
                logger.info("succeed in handling flowerResult");
            } else {
                logger.info("fail to handle updateOrderResult Result:" + updateOrderResult.getJSONObject("data").toString());
            }
            outputResult(response, "success");

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            outputResult(response, "ex");
        }

    }


    /**
     * 获取所有request请求参数key-value
     *
     * @param request
     * @return
     */
    public static Map<String, String> getRequestParams(HttpServletRequest request) {

        Map<String, String> params = new HashMap<String, String>();
        if (null != request) {
            Set<String> paramsKey = request.getParameterMap().keySet();
            for (String key : paramsKey) {
                params.put(key, request.getParameter(key));
            }
        }
        return params;
    }


    /**
     * 验签
     *
     * @param params
     * @return
     */
    private boolean verifySignForRequest(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    PropFile.getProps("alipay.public.key.9999"),
                    PropFile.getProps("sign.charset"),
                    AlipayConstants.SIGN_TYPE_RSA2);
        } catch (AlipayApiException aae) {
            logger.error(aae.getMessage(), aae);
            return false;
        }
    }


    private AlipaySyncNotify toAlipaySyncNotify(Map<String, String> params) {
        AlipaySyncNotify alipaySyncNotify = new AlipaySyncNotify();
        alipaySyncNotify.setNotifyId(params.get("notify_id"));
        alipaySyncNotify.setNotifyType(params.get("notify_type"));
        alipaySyncNotify.setNotifyTime(params.get("notify_time"));
        alipaySyncNotify.setTradeNo(params.get("trade_no"));
        alipaySyncNotify.setAppId(params.get("app_id"));
        alipaySyncNotify.setOutTradeNo(params.get("out_trade_no"));
        alipaySyncNotify.setOutBizNo(params.get("out_biz_no"));
        alipaySyncNotify.setBuyerId(params.get("buyer_id"));
        alipaySyncNotify.setBuyerLogonId(params.get("buyer_logon_id"));
        alipaySyncNotify.setSellerId(params.get("seller_id"));
        alipaySyncNotify.setSellerLogonId(params.get("seller_email"));
        alipaySyncNotify.setTradeStatus(params.get("trade_status"));
        alipaySyncNotify.setTotalAmount(params.get("total_amount"));
        alipaySyncNotify.setReceiptAmount(params.get("receipt_amount"));
        alipaySyncNotify.setInvoiceAmount(params.get("invoice_amount"));
        alipaySyncNotify.setBuyerPayAmount(params.get("buyer_pay_amount"));
        alipaySyncNotify.setPointAmount(params.get("point_amount"));
        alipaySyncNotify.setRefundFee(params.get("refund_fee"));
        alipaySyncNotify.setSendBackFee(params.get("send_back_fee"));
        alipaySyncNotify.setGmtCreate(params.get("gmt_create"));
        alipaySyncNotify.setGmtPayment(params.get("gmt_payment"));
        alipaySyncNotify.setGmtRefund(params.get("gmt_refund"));
        alipaySyncNotify.setGmtClose(params.get("gmt_close"));
        alipaySyncNotify.setFundBillList(params.get("fund_bill_list"));
        alipaySyncNotify.setVoucherDetailList(params.get("voucher_detail_list"));
        alipaySyncNotify.setCreateTime(DateUtils.getString(new Date(), DateUtils.FMT_SECOND_INT));
        return alipaySyncNotify;
    }


    //手工创建loginSession
    private Map<String, String> mockRequestSession(HttpServletRequest req) {
        Map<String, String> session = new HashMap<String, String>();
        session.put("sessionId", req.getSession().getId());
        session.put("domId", "-1");
        session.put("userIp", Utils.getRemoteAddr(req));
        return session;
    }


    private JSONObject postRequestWrapper(Map<String, String> loginSession, String url, JSONObject inputParam) {
        String sessionId = loginSession.get("sessionId");
        WebProxy.createSequenceForSession(sessionId);
        JSONObject result = getPostRequestResult(loginSession, url, inputParam.toString());
        WebProxy.removeSequenceForSession(sessionId);
        return result;
    }


    private synchronized boolean isHasUpdateOrderStatus(Map<String, String> loginSession, String orderNo) throws Exception {
        JSONObject orderStatusParam = new JSONObject();
        orderStatusParam.put("orderNo", orderNo);
        JSONObject invokeResult = postRequestWrapper(loginSession, "/ec/vip/queryVipOrderInfo.html", orderStatusParam);
        boolean isSuccess = invokeResult.optBoolean("success");
        if (!isSuccess) {
            throw new Exception("invoke loadOrderLatestStatus interface exception");
        }
        JSONObject pbqResult = invokeResult.optJSONObject("data");
        if (pbqResult == null) {
            throw new Exception("loadOrderLatestStatus interface no data.");
        }
        int resultCode = pbqResult.optInt("resultCode");
        if (resultCode != 200) {
            throw new Exception("loadOrderLatestStatus interface query data exception[" + pbqResult.optString("resultReason") + "]");
        }
        JSONObject resultData = pbqResult.optJSONObject("resultData");
        int statusGet = resultData.optInt("payStatus");
        if (statusGet != 4800 && statusGet != 4802) {
            logger.info("orderNoInput[" + orderNo + "] has Updated status[" + statusGet + "],ignore the alipay notify");
            return true;
        }
        return false;
    }

    /**
     * 后台台账列表
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toListCustomerOrder.htm")
    public ModelAndView toListCustomerOrder(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        boolean showEdit = false;
        boolean showAppend = false;
        String manageParamsnMap = (String) request.getSession().getAttribute("manageParams");
        final Map<String, String> permissionMap = (Map<String, String>) request.getSession().getAttribute("permission");
        final String permission = permissionMap.get("crmVip");

        logger.info(" VipCustomerController toListCustomerOrder permission:" + permissionMap.get("crmVip"));
        if (StringUtils.isNotEmpty(permission) && permission.contains("crmEdit")) {
            showEdit = true;
        }
        if (StringUtils.isNotEmpty(permission) && permission.contains("crmAppend")) {
            showAppend = true;
        }
        JSONObject manageParamsn = JSONObject.fromObject(manageParamsnMap);

        logger.info(" VipCustomerController toListCustomerOrder manageParamsn:" + manageParamsn);
        int customer = 0;
        if (null != manageParamsn.get("customer")) {
            customer = Integer.parseInt(manageParamsn.get("customer").toString());
            if (customer == 0) {
                mv.addObject("customerName", "银行");
            } else if (customer == 1) {
                mv.addObject("customerName", "非银");
            } else if (customer == 2) {
                mv.addObject("customerName", "企业");
            } else if (customer == 3) {
                mv.addObject("customerName", "中介");
            }
        }
        mv.addObject("customer", customer);
        mv.addObject("showEdit", showEdit);
        mv.addObject("showAppend", showAppend);
        mv.setViewName("crm/vip/listCustomerOrder");
        return mv;
    }

    /**
     * 后台台账详情
     *
     * @param request
     * @return
     * @throws UnsupportedEncodingException
     */
    @RequestMapping(value = "/orderDetails.htm")
    public ModelAndView orderDetails(final HttpServletRequest request) throws UnsupportedEncodingException {
        ModelAndView mv = new ModelAndView();
        Map<String, String> session = (Map<String, String>) request.getSession().getAttribute("session");
        String manageParamsnMap = (String) request.getSession().getAttribute("manageParams");
        JSONObject manageParamsn = JSONObject.fromObject(manageParamsnMap);
        String orderParams = request.getParameter("orderParams");
        boolean encryptMobile = false;
        final Map<String, String> permissionMap = (Map<String, String>) request.getSession().getAttribute("permission");
        final String permission = permissionMap.get("crmVip");
        if (StringUtils.isNotEmpty(permission) && permission.contains("encryptMobile")) {
            encryptMobile = true;
        }
        if (StringUtils.isNotBlank(orderParams)) {
            request.getSession().setAttribute("orderParams", java.net.URLDecoder.decode(orderParams, "UTF-8"));
        }
        String employeeNo = session.get("employeeNo");
        String orderNo = (String) request.getParameter("orderNo");
        String customerId = (String) request.getParameter("customerId");
        String customerType = (String) request.getParameter("customerType");
        boolean isEdit = Boolean.parseBoolean(request.getParameter("isEdit"));
        JSONObject data = new JSONObject();
        data.put("orderNo", orderNo);

        JSONObject responseResult = getPostRequestResult(session, "/crm/customer/listOfOrder.htm", data.toString());
        if (responseResult.getBoolean("success") && responseResult.getInt("responseCode") == 200) {
            responseResult = parseInsertResponseResult(responseResult);
        }
        data.put("customerId", customerId);
        data.put("customerType", customerType);
        JSONObject account = getPostRequestResult(session, "/crm/customer/getAccount.htm", data.toString());
        JSONArray accountList = null;
        if (account.getBoolean("success") && account.getInt("responseCode") == 200) {
            accountList = account.containsKey("data") ? (account.getJSONObject("data").getJSONArray("data")) : null;
        }
        JSONObject discount = getPostRequestResult(session, "/crm/vip/queryVipDiscountList.htm",data.toString());
        logger.info("discount:"+discount.toString());
        JSONArray discountList = null;
        if (discount.getBoolean("success") && discount.getInt("responseCode") == 200) {
            discountList = discount.containsKey("data") ? (discount.getJSONArray("data")) : null;
        }
        logger.info("discountList:"+discountList.toString());
        mv.addObject("urlPrefix", ConfigManager.INSTANCE.getConfig().getProperty("fastDFS.server"));
        mv.addObject("isEdit", !isEdit);
        mv.addObject("data", responseResult);
        mv.addObject("accountList", accountList);
        mv.addObject("employeeNo", employeeNo);
        mv.addObject("encryptMobile", encryptMobile);
        mv.addObject("discountList", discountList);
        int customer = 0;
        if (null != manageParamsn.get("customer")) {
            customer = Integer.parseInt(manageParamsn.get("customer").toString());
            mv.addObject("customer", customer);
            if (customer == 0) {
                mv.addObject("customerName", "银行");
            } else if (customer == 1) {
                mv.addObject("customerName", "非银");
            } else if (customer == 2) {
                mv.addObject("customerName", "企业");
            } else if (customer == 3) {
                mv.addObject("customerName", "中介");
            }
        }
        mv.setViewName("crm/vip/customerOrderDetail");
        return mv;
    }

    private JSONObject parseInsertResponseResult(JSONObject result) {
        return result.containsKey("data") ? (result.getJSONObject("data").getJSONArray("data")).getJSONObject(0) : null;
    }


    /**
     * 9999会员专属服务申请页面
     *
     * @param request
     * @return
     * @throws UnsupportedEncodingException
     */
    @RequestMapping(value = "/toApplyVipInformation.htm")
    public ModelAndView toApplyVipInformation(final HttpServletRequest request) throws UnsupportedEncodingException {
        final ModelAndView mv = new ModelAndView();
        String userId = request.getParameter("userId");
        boolean isOrder = false;

        Map<String, String> accountInfoMap2 = (Map<String, String>) request.getSession().getAttribute("accountInfoParams");
        String vipType = "";
        if(null != accountInfoMap2 && !accountInfoMap2.isEmpty()){
            vipType = accountInfoMap2.get("vipType");
        }
        HttpSession session = request.getSession();
        String params = "{'userId':'" + userId + "'}";
        Map<String, String> sessionMap = (Map<String, String>) session.getAttribute("session");
        JSONObject resultObj = getPostRequestResult(sessionMap, "/ec/vip/queryAccountDetail.html", params);
        JSONObject resultData = new JSONObject();
        boolean isSuccess = resultObj.optBoolean("success");
        if (isSuccess) {
            resultData = resultObj.optJSONObject("data");
            String customerType = resultData.optString("customerType");
            String customerId = resultData.optString("customerId");
            String customerName = resultData.optString("customerName");
            String userName = resultData.optString("userName");
            String contactName = resultData.optString("contactName");
            //默认为 1 一级会员
            if(StringUtils.isBlank(vipType)){
                vipType = resultData.optString("vipType","1");
                if(StringUtils.isNotBlank(vipType) && vipType.equals("0")){
                    vipType = "1";
                }
            }
            String isVip = resultData.optString("isVip","0");

            String args = "{'userName':'" + userName + "' ,'type':2}";
            JSONObject orderListObj = getPostRequestResult(sessionMap, "/ec/vip/queryVipOrderList.html", args);
            JSONArray orderListData = new JSONArray();
            if (resultObj.containsKey("data")) {
                orderListData = orderListObj.optJSONArray("data");
                if (null != orderListData && !orderListData.isEmpty()) {
                    isOrder = true;
                }
            }
            JSONObject discountsJson = packageVipdiscountsInfo(sessionMap,mv);
            String discounts1 = discountsJson.optString("discounts1");
            String discounts2 = discountsJson.optString("discounts2");
            String discounts3 = discountsJson.optString("discounts3");
            String discounts = discountsJson.optString("discounts" + vipType);

            mv.addObject("discounts1",discounts1);
            mv.addObject("discounts2",discounts2);
            mv.addObject("discounts3",discounts3);
            mv.addObject("discounts",discounts);

            //账号信息session
            Map<String, String> accountInfoMap = new HashMap<>();
            accountInfoMap.put("userId", userId);
            accountInfoMap.put("customerType", customerType);
            accountInfoMap.put("customerId", customerId);
            accountInfoMap.put("customerName", customerName);
            accountInfoMap.put("userName", userName);
            accountInfoMap.put("contactName", contactName);
            accountInfoMap.put("vipType", vipType);
            accountInfoMap.put("isVip", isVip);
            request.getSession().setAttribute("accountInfoParams", accountInfoMap);

            mv.addObject("isOrder", isOrder);
            mv.addObject("userId", userId);
            mv.addObject("customerType", customerType);
            mv.addObject("customerId", customerId);
            mv.addObject("customerName", customerName);
            mv.addObject("userName", userName);
            mv.addObject("vipType", vipType);
            mv.addObject("isVip", isVip);

        }

        mv.setViewName("crm/vip/applyVipInformation");
        return mv;
    }


    private JSONObject packageVipdiscountsInfo(Map<String, String> sessionMap, ModelAndView mv){
        JSONObject json = new JSONObject();
        //司庆活动参数 vipDiscountType ： 101 discountsType: 1:折扣优惠
        String args = "{'vipDiscountType':'101' ,'discountsType':'1'}";
        mv.addObject("vipDiscountType",VIP_DISCOUNT_TYPE_517);
        mv.addObject("discountsType","1");
        JSONObject vipDiscountsListObj = getPostRequestResult(sessionMap, "/html/vip/QueryVipDiscountsInfoRequest.html", args);
        logger.info("packageVipdiscountsInfo" + vipDiscountsListObj.toString());
        if(vipDiscountsListObj.containsKey("data")){
            json = vipDiscountsListObj.optJSONObject("data");
        }
        return json;
    }


    /**
     * 9999会员专属服务申请书页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toVipApplication.htm")
    public ModelAndView toVipApplication(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        String vipType = request.getParameter("vipType");
        String statusType = request.getParameter("statusType");
        String isRenew = request.getParameter("isRenew");
        Map<String, String> accountInfoMap = (Map<String, String>) request.getSession().getAttribute("accountInfoParams");
        accountInfoMap.put("vipType", vipType);
        request.getSession().setAttribute("accountInfoParams", accountInfoMap);

        String userId = accountInfoMap.get("userId");
        String customerType = accountInfoMap.get("customerType");
        String customerId = accountInfoMap.get("customerId");
        String customerName = accountInfoMap.get("customerName");
        String userName = accountInfoMap.get("userName");
        String contactName = accountInfoMap.get("contactName");


        HttpSession session = request.getSession();
        String params = "{'userId':'" + userId + "'}";
        Map<String, String> sessionMap = (Map<String, String>) session.getAttribute("session");
        JSONObject resultObj = getPostRequestResult(sessionMap, "/ec/vip/queryAccountDetail.html", params);

        String nowDate = DateUtils.getString(new Date(), DateUtils.FMT_DATE);
        mv.addObject("userId", userId);
        mv.addObject("customerType", customerType);
        mv.addObject("customerId", customerId);
        mv.addObject("customerName", customerName);
        mv.addObject("userName", userName);
        mv.addObject("contactName", contactName);
        mv.addObject("nowDate", nowDate);
        mv.addObject("statusType", statusType);
        mv.addObject("vipType", vipType);
        if(StringUtils.isNotBlank(isRenew)){
            mv.addObject("isRenew",isRenew);
        }
        mv.addObject("vipTypeName", vipTypeMap.get(vipType));
        mv.setViewName("crm/vip/vipApplication");
        return mv;
    }


    /**
     * 9999会员专属服务申请订单页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toApplyVipOrder.htm")
    public ModelAndView toApplyVipOrder(final HttpServletRequest request) throws Exception {
        final ModelAndView mv = new ModelAndView();
        String type = request.getParameter("type");//1：新增；2：修改
        String statusType = request.getParameter("statusType");//1：所有订单；2：未交费，待续费订单
        String isRenew = request.getParameter("isRenew");//1：是否是续费
        String vipType = request.getParameter("vipType");

        Map<String, String> accountInfoMap = (Map<String, String>) request.getSession().getAttribute("accountInfoParams");
        String isVip = accountInfoMap.get("isVip");
        String userId = accountInfoMap.get("userId");
        String customerType = accountInfoMap.get("customerType");
        String customerId = accountInfoMap.get("customerId");
        String customerName = accountInfoMap.get("customerName");
        String userName = accountInfoMap.get("userName");
        HttpSession session = request.getSession();
        Map<String, String> sessionMap = (Map<String, String>) session.getAttribute("session");
        if (StringUtils.equals("2", type)) {
            String orderNo = request.getParameter("orderNo");
            String args = "{'orderNo':'" + orderNo + "'}";
            JSONObject resultObj = getPostRequestResult(sessionMap, "/ec/vip/queryVipOrderDetail.html", args);
            if (resultObj.containsKey("data")) {
                JSONObject orderData = resultObj.getJSONObject("data").getJSONObject("orderData");
                mv.addObject("orderData", URLEncoder.encode(orderData.toString(), "UTF-8"));
                mv.addObject("orderNo", orderNo);
            }
            mv.addObject("vipType", vipType);
        }else{
            vipType = accountInfoMap.get("vipType");
            mv.addObject("vipType", vipType);
        }

        JSONObject discountsJson = packageVipdiscountsInfo(sessionMap,mv);
        String discounts = discountsJson.optString("discounts" + vipType);

        mv.addObject("discounts", discounts);
        mv.addObject("vipDiscountType", VIP_DISCOUNT_TYPE_517);
        mv.addObject("type", type);
        mv.addObject("statusType", statusType);
        mv.addObject("userId", userId);
        mv.addObject("customerType", customerType);
        mv.addObject("customerId", customerId);
        mv.addObject("customerName", customerName);
        mv.addObject("userName", userName);
        mv.addObject("isVip", isVip);
        if(StringUtils.isNotBlank(isRenew)){
            mv.addObject("isRenew",isRenew);
        }
        mv.setViewName("crm/vip/applyVipOrder");
        return mv;
    }

    /**
     * 9999会员成功页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toVipSuccess.htm")
    public ModelAndView toVipSuccess(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        String customerId = request.getParameter("customerId");
        HttpSession session = request.getSession();
        String args = "{'customerId':'" + customerId + "'}";
        Map<String, String> sessionMap = (Map<String, String>) session.getAttribute("session");
        JSONObject customerDetailObj = getPostRequestResult(sessionMap, "/ec/vip/queryVipCustomerDetail.html", args);
        JSONObject customerDetaiData = new JSONObject();
        if (customerDetailObj.containsKey("data")) {
            customerDetaiData = customerDetailObj.optJSONObject("data");
            mv.addObject("customerName", customerDetaiData.optString("customerName"));
            mv.addObject("vipId", customerDetaiData.optString("vipId"));
            mv.addObject("vipType", vipTypeMap.get(customerDetaiData.optString("vipType")));
            mv.addObject("startTime", customerDetaiData.optString("startTime"));
            mv.addObject("endTime", customerDetaiData.optString("endTime"));
        }
        mv.setViewName("crm/vip/vipSuccess");
        return mv;
    }


    /**
     * 9999会员线下确认页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toOfflineConfirmation.htm")
    public static ModelAndView toOfflineConfirmation(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        mv.setViewName("crm/vip/offlineConfirmation");
        return mv;
    }


    /**
     * 9999会员订单详情
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toMyOrderDetail.htm")
    public ModelAndView toMyOrderDetail(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        String type = request.getParameter("type");
        String statusType = request.getParameter("statusType");
        Map<String, String> accountInfoMap = (Map<String, String>) request.getSession().getAttribute("accountInfoParams");
        String userId = accountInfoMap.get("userId");
        String customerType = accountInfoMap.get("customerType");
        String customerId = accountInfoMap.get("customerId");
        String customerName = accountInfoMap.get("customerName");
        String userName = accountInfoMap.get("userName");
        String isVip = accountInfoMap.get("isVip");
        String vipType = accountInfoMap.get("vipType");
        mv.addObject("userId", userId);
        mv.addObject("customerType", customerType);
        mv.addObject("customerId", customerId);
        mv.addObject("customerName", customerName);
        mv.addObject("userName", userName);
        mv.addObject("type", type);
        mv.addObject("statusType", statusType);
        mv.addObject("isVip", isVip);
        mv.addObject("vipType", vipType);

        Map<String, String> sessionMap = (Map<String, String>) request.getSession().getAttribute("session");
        JSONObject discountsJson = packageVipdiscountsInfo(sessionMap,mv);
        mv.addObject("discountsJson",discountsJson.toString());

        mv.setViewName("crm/vip/myOrderDetail");
        return mv;
    }


    /**
     * 下载会员申请书
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "/downVipTempletFile.htm")
    public void downTempletFile(final HttpServletRequest request, final HttpServletResponse response) {
        String tplNameEncode = request.getParameter("tplName");
        File downTempletFile = null;
        try {
            String tplName = URLDecoder.decode(tplNameEncode, "UTF-8");
            downTempletFile = new File(request.getSession().getServletContext().getRealPath("/excel/" + tplName + ".docx"));
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        if (downTempletFile != null) {
            ExcelUtil.downloadFile(response, downTempletFile);
        }
    }


    /**
     * 我的9999会员页面
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toMyVip.htm")
    public ModelAndView toMyVip(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        String userId = request.getParameter("userId");
        String customerId = request.getParameter("customerId");
        String customerType = "";
        String customerName = "";
        String userName = "";
        String vipType = "";
        String isVip = "";
        HttpSession session = request.getSession();
        String args = "{'customerId':'" + customerId + "'}";
        Map<String, String> sessionMap = (Map<String, String>) session.getAttribute("session");
        JSONObject customerDetailObj = getPostRequestResult(sessionMap, "/ec/vip/queryVipCustomerDetail.html", args);
        JSONObject customerDetaiData = new JSONObject();
        if (customerDetailObj.containsKey("data")) {
            customerDetaiData = customerDetailObj.optJSONObject("data");
            customerName = customerDetaiData.optString("customerName");
            vipType = customerDetaiData.optString("vipType");
            isVip = customerDetaiData.optString("isVip");

            mv.addObject("customerName", customerDetaiData.optString("customerName"));
            mv.addObject("vipId", customerDetaiData.optString("vipId"));
            mv.addObject("startTime", customerDetaiData.optString("startTime"));
            mv.addObject("endTime", customerDetaiData.optString("endTime"));
            mv.addObject("vipType", vipType);
            mv.addObject("isVip", isVip);
        }

        String params = "{'userId':'" + userId + "'}";
        JSONObject resultObj = getPostRequestResult(sessionMap, "/ec/vip/queryAccountDetail.html", params);
        JSONObject resultData = new JSONObject();
        boolean isSuccess = resultObj.optBoolean("success");
        if (isSuccess) {
            resultData = resultObj.optJSONObject("data");
            customerType = resultData.optString("customerType");
            userName = resultData.optString("userName");
            mv.addObject("customerType", customerType);
            mv.addObject("userName", userName);
        }

        //账号信息session
        Map<String, String> accountInfoMap = new HashMap<>();
        accountInfoMap.put("userId", userId);
        accountInfoMap.put("customerType", customerType);
        accountInfoMap.put("customerId", customerId);
        accountInfoMap.put("customerName", customerName);
        accountInfoMap.put("userName", userName);
        accountInfoMap.put("userName", userName);
        accountInfoMap.put("vipType", vipType);
        accountInfoMap.put("isVip", isVip);
        request.getSession().setAttribute("accountInfoParams", accountInfoMap);

        JSONObject discountsJson = packageVipdiscountsInfo(sessionMap,mv);
        String discounts1 = discountsJson.optString("discounts1");
        String discounts2 = discountsJson.optString("discounts2");
        String discounts3 = discountsJson.optString("discounts3");
        String discounts = discountsJson.optString("discounts" + vipType);

        mv.addObject("discounts1",discounts1);
        mv.addObject("discounts2",discounts2);
        mv.addObject("discounts3",discounts3);
        mv.addObject("discounts",discounts);

        mv.addObject("userId", userId);
        mv.addObject("customerId", customerId);
        mv.setViewName("crm/vip/myVipUpgrade");
        return mv;
    }


    /**
     * 后台台账列表
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/toListVipOrder.htm")
    public ModelAndView toListVipOrder(final HttpServletRequest request) {
        final ModelAndView mv = new ModelAndView();
        boolean showEdit = false;
        boolean showAppend = false;
        final Map<String, String> permissionMap = (Map<String, String>) request.getSession().getAttribute("permission");
        final String permission = permissionMap.get("crmVip");
        String manageParamsnMap = (String) request.getSession().getAttribute("manageParams");
        JSONObject manageParamsn = JSONObject.fromObject(manageParamsnMap);
        int customer = 0;
        if (null != manageParamsn.get("customer")) {
            customer = Integer.parseInt(manageParamsn.get("customer").toString());
        }
        ;
        mv.addObject("customer", customer);
        logger.info(" VipCustomerController toListCustomerOrder permission:" + permissionMap.get("crmVip"));
        if (StringUtils.isNotEmpty(permission) && permission.contains("crmEdit")) {
            showEdit = true;
        }
        logger.info(" VipCustomerController toListCustomerOrder permission:" + permissionMap.get("crmAppend"));
        if (StringUtils.isNotEmpty(permission) && permission.contains("crmAppend")) {
            showAppend = true;
        }
        String orderParams = (String) request.getSession().getAttribute("orderParams");
        if (StringUtils.isNotBlank(orderParams)) {
            mv.addObject("orderParams", JSONObject.fromObject(orderParams));
            request.getSession().removeAttribute("orderParams");
        }
        mv.addObject("showAppend", showAppend);
        mv.addObject("showEdit", showEdit);
        mv.setViewName("crm/vip/listVipOrder");
        return mv;
    }

    /**
     * 保存订单
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/saveCrmVipOrder.htm")
    @ResponseBody
    public String saveCrmVipOrder(HttpServletRequest request) {
        Map<String, String> sessionMap = (Map<String, String>) request.getSession().getAttribute("session");
        JSONObject params = this.buildOrderInfo(request);
        logger.info("saveCrmVipOrder params:" + params.toString());
        JSONObject pbqResult = new JSONObject();
        pbqResult.put("success", true);
        pbqResult.put("responseCode", 200);
        if (null != params && params.size() != 0) {
            JSONObject resultObj = getPostRequestResult(sessionMap, "/crm/order/updateOrderInfo.htm", params.toString());
            if (resultObj.containsKey("data")) {
                resultObj = resultObj.getJSONObject("data");
                Map<String, String> orderInfo = Maps.newHashMap();
                orderInfo.put("amout", StringUtils.stripToEmpty(resultObj.optString("amount")));
                orderInfo.put("orderNo", StringUtils.stripToEmpty(resultObj.optString("orderNoStr")));
                orderInfo.put("endTime", StringUtils.stripToEmpty(resultObj.optString("endTime")));
                orderInfo.put("activityCode", StringUtils.stripToEmpty(resultObj.optString("activityCode")));
                orderInfo.put("accounts", StringUtils.stripToEmpty(resultObj.optString("userIds")));
                orderInfo.put("startTime", StringUtils.stripToEmpty(resultObj.optString("startTime")));
                orderInfo.put("userId", StringUtils.stripToEmpty(resultObj.optString("userId")));
                orderInfo.put("isSendCoupon", StringUtils.stripToEmpty(resultObj.optString("isSendCoupon")));
                orderInfo.put("isSendFlower", StringUtils.stripToEmpty(resultObj.optString("isSendFlower")));
                orderInfo.put("vipType",StringUtils.stripToEmpty(resultObj.optString("vipType")));
                orderInfo.put("isVip",StringUtils.stripToEmpty(resultObj.optString("isVip")));
                //送花
                MemberServiceInterfaceController memberServiceInterfaceController = new MemberServiceInterfaceController();
                if (resultObj.optBoolean("isSendFlowers")) {
                    logger.info("send flower begin -------------> info:" + orderInfo.toString());
                    memberServiceInterfaceController.fourNineMemberActivity(sessionMap, orderInfo);
                    logger.info("send flower end  ------------->");
                }
                //取消订单
                if (resultObj.optBoolean("cancelOrder")) {
                    logger.info("cancel order begin -------------> info:" + orderInfo.toString());
                    memberServiceInterfaceController.fourNineMemberActivityRollback(sessionMap, orderInfo);
                    logger.info("cancel order end -------------> ");
                }
            } else {
                pbqResult.put("success", false);
                pbqResult.put("responseCode", 500);
                pbqResult.put("errorMsg", "系统错误");
            }
        } else {
            pbqResult.put("success", false);
            pbqResult.put("responseCode", 301);
            pbqResult.put("errorMsg", "缺少参数");
        }
        return pbqResult.toString();
    }

    private JSONObject buildOrderInfo(HttpServletRequest request) {
        JSONObject orderInfo = new JSONObject();
        String[] orderColumn = {"year", "customerId", "applyAccountId", "orderNo", "employeeNo", "endDate", "endTime", "startTime", "startDate", "expressNum", "isExpress",
                "expressStatus", "address", "cityCode", "provinceCode", "identificationNumber", "invoiceTitle", "isInvoice", "payStatus", "customerType", "recommenderId", "customerName", "orderId", "lastPayStatus", "remark",
                "cityId", "provinceId", "invoiceType1", "invoiceType2", "depositBank", "depositAccountNumber", "fixPhone", "invoiceAddress", "sendCoupons", "sendFlowers", "mobile", "vipNo", "expressNumOld", "draweeAccountId",
                "draweeAccountNumber", "payType","vipType","totalMoney","vipDiscount","vipDiscountType","discountRemarkStr"};
        for (String column : orderColumn) {
            orderInfo.put(column, StringUtils.stripToEmpty(request.getParameter(column)));
        }
        return orderInfo;
    }

    @RequestMapping(value = "/rapidSaveVipOrder.htm")
    @ResponseBody
    public String saveVipOrder(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> session = (Map<String, String>) request.getSession().getAttribute("session");
        String paramString = URLDecoder.decode(request.getParameter("manageParams"), "UTF-8");
        String userId = session.get("employeeNo");
        JSONObject json = JSONObject.fromObject(paramString);
        json.put("updateNo", userId);
        JSONObject responseResult = new JSONObject();
        JSONObject param = this.buildVipOrderInfo(json);
        responseResult = getPostRequestResult(session == null ? new HashMap<String, String>() : session, "/ec/vip/saveVipOrder.html", param.toString());
        return responseResult.toString();
    }

    private JSONObject buildVipOrderInfo(JSONObject json) {
        JSONObject order = new JSONObject();
        order.put("type", 1);
        order.put("payType", 2);
        order.put("isInvoice", 0);
        order.put("payStatus", 4800);
        order.put("isExpress", 0);
        order.put("expressStatus", 0);
        order.put("source", 4);
        order.put("invoiceType1", 1);
        order.put("updateTime", DateUtils.getString(new Date(), DateUtils.FMT_SECOND_INT));
        for (Object column : json.keySet()) {
            String name = StringUtils.stripToEmpty(json.optString((String) column));
            order.put(column, name);
        }
        logger.info("saveVipOrder:" + order.toString());
        return order;
    }


    public final static String[] BANK_EXPORT_HEADER = new String[]{"单位简称", "联系人姓名", "申请账号", "会员编号", "缴费状态", "金额", "支付方式", "开发人", "所属部门", "申请日期", "会员到期日", "订单来源"};
    public final static String[] COMPANY_EXPORT_HEADER = new String[]{"单位全称", "联系人姓名", "申请账号", "会员编号", "缴费状态", "金额", "支付方式", "开发人", "所属部门", "申请日期", "会员到期日", "订单来源"};

    @RequestMapping(value = "/exportVipOrderInfo.htm")
    public void exportVipCustomerOrder(final HttpServletRequest request, final HttpServletResponse response)
            throws Exception {
        String paramString = URLDecoder.decode(request.getParameter("manageParams"), "UTF-8");
        JSONObject param = JSONObject.fromObject(paramString);
        String customer = param.optString("customer");
        final Map<String, String> session = (Map<String, String>) request.getSession().getAttribute("session");
        JSONArray result = new JSONArray();
        try {
            JSONObject pageData;
            int pageNum = 0;
            final Integer pageSize = 2500;
            while (true) {
                param.put("pageIndex", pageNum);
                param.put("pageSize", pageSize);
                pageData = getPostRequestResult(session, "/crm/vip/listVipOrder.html", param.toString());
                logger.info("/crm/vip/listVipOrder.html  result--->" + pageData.toString());
                JSONArray pageRowDatas = pageData.optJSONObject("data").optJSONArray("data");
                result.addAll(pageRowDatas);
                pageNum++;
                if (pageRowDatas == null) {
                    break;
                }
                if (pageRowDatas.size() < pageSize) {
                    break;
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        final String exportExcelName = "会员订单";
        if (StringUtils.equals("0", customer) || StringUtils.equals("1", customer)) {
            ExcelUtil.downloadBigFile(exportExcelName, response,
                    ExcelUtil.createBigExcel(result, Arrays.asList(BANK_EXPORT_HEADER),
                            Arrays.asList(ExportConstant.COLUMN_CRM_VIP_ORDER_LIST), "会员订单台账"));
        } else if (StringUtils.equals("2", customer) || StringUtils.equals("3", customer)) {
            ExcelUtil.downloadBigFile(exportExcelName, response,
                    ExcelUtil.createBigExcel(result, Arrays.asList(COMPANY_EXPORT_HEADER),
                            Arrays.asList(ExportConstant.COLUMN_CRM_VIP_ORDER_LIST), "会员订单台账"));

        }
    }
}
