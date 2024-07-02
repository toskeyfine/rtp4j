package com.toskey.media.rtp4j.listener;

import com.toskey.media.rtp4j.RTP;

/**
 * IWrapperListener
 *
 * @author lis
 * @version 1.0
 * @description TODO
 * @date 2024/7/2 14:33
 */
public interface IWrapperListener {

    default void onBefore(byte[] raw) {

    }

    default void onSuccess(RTP rtp) {

    }

    default void onError(Exception e) {

    }
}
