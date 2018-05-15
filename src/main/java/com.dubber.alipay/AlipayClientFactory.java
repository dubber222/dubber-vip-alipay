package com.dubber.alipay;

import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConstants;
import com.alipay.api.DefaultAlipayClient;

/**
 * Created on 2018/5/16.
 *
 * @author dubber
 */
public class AlipayClientFactory {
    private static AlipayClient alipayClient;


    /**
     * 获得API调用客户端
     *
     * @return
     */
    public static AlipayClient getAlipayClient() {

        if (null == alipayClient) {
            String alipayGateway = PropUtils.getProps("alipay.gateway.url");
            String appId = PropUtils.getProps("app.id");
            String appPrivateKey = PropUtils.getProps("app.private.key");
            String charset = PropUtils.getProps("data.charset");
            String alipayPublicKey = PropUtils.getProps("alipay.public.key");
            alipayClient = new DefaultAlipayClient(
                    alipayGateway
                    , appId
                    , appPrivateKey
                    , "json"
                    , charset
                    , alipayPublicKey
                    , AlipayConstants.SIGN_TYPE_RSA2
            );
        }
        return alipayClient;
    }

}
