package com.wolkabout.wolk.firmwareupdate;

import com.wolkabout.wolk.firmwareupdate.model.UpdateError;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class UrlFileDownloader {

    public interface Callback {
        void onError(UpdateError error);
        void onFileReceived(String fileName, byte[] bytes);
    }

    private Callback callback;
    private String url;

    private static final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Runnable downloadTask = new Runnable() {
        @Override
        public void run() {
            download();
        }
    };

    private Future<?> runningDownloadTask;

    public void downloadFile(String url, Callback callback) {
        stopDownload();

        this.url = url;
        this.callback = callback;

        runningDownloadTask = executor.submit(downloadTask);
    }

    public void abort() {
        stopDownload();
    }

    private void download() {
        try {
            final URL remoteFile = new URL(url);
            final InputStream inputStream = remoteFile.openStream();
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int read;
            byte[] data = new byte[16384];

            while ((read = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, read);
            }

            buffer.flush();

            final String[] urlParts = url.split("/");
            final String fileName = urlParts[urlParts.length - 1];

            callback.onFileReceived(fileName, buffer.toByteArray());
        } catch (MalformedURLException e) {
            callback.onError(UpdateError.MALFORMED_URL);
        } catch (Exception e) {
            callback.onError(UpdateError.UNSPECIFIED_ERROR);
        }
    }

    private void stopDownload() {
        if (runningDownloadTask == null || runningDownloadTask.isDone()) {
            return;
        }

        runningDownloadTask.cancel(true);
    }
}