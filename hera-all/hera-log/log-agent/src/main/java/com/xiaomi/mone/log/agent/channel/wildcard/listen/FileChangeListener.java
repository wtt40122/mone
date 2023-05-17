package com.xiaomi.mone.log.agent.channel.wildcard.listen;

/**
 * @author wtt
 * @version 1.0
 * @description
 * @date 2023/5/16 15:41
 */
public interface FileChangeListener {

    void onFileCreate(final String file, final String uniqueMark);

    /**
     * File changed Event.
     *
     * @param file The file changed
     */
    void onFileChange(final String file, final String uniqueMark);

    /**
     * File deleted Event.
     *
     * @param file The file deleted
     */
    void onFileDelete(final String file, final String uniqueMark);

}
