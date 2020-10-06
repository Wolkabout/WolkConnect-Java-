/*
 * Copyright (c) 2018 WolkAbout Technology s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.wolkabout.wolk.filemanagement;

import com.wolkabout.wolk.filemanagement.model.device2platform.FileTransferError;
import com.wolkabout.wolk.filemanagement.model.device2platform.FileTransferStatus;
import com.wolkabout.wolk.filemanagement.model.platform2device.FileInit;
import jdk.internal.jline.internal.Log;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileDownloadSession {

    private static final Logger LOG = LoggerFactory.getLogger(FileDownloadSession.class);

    // The constant values
    private static final int MINIMUM_PACKET_SIZE = 65;
    private static final int PREVIOUS_HASH_SIZE = 32;
    private static final int CURRENT_HASH_SIZE = 32;
    private static final int CHUNK_SIZE = 1000000;
    private static final int MAX_RETRY = 3;
    private static final int MAX_RESTART = 3;
    // The executor
    private static final ExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    // The input data
    private final FileInit initMessage;
    private final Callback callback;
    // The collected data
    private final List<Integer> chunkSizes;
    private final List<Byte> bytes;
    private final List<byte[]> hashes;
    // The main indicators of state
    private boolean running;
    private boolean success;
    private boolean aborted;
    private int currentChunk;
    private int chunkRetryCount;
    private int restartCount;
    // The end status variables
    private FileTransferStatus status;
    private FileTransferError error;


    /**
     * The default constructor for the class. Bases the download session off the passed message data about the file
     * that needs to be received, and contains external calls to request more incoming data, and notify of the finished
     * status.
     *
     * @param initMessage The parsed message object that contains information about a file that needs to be transferred.
     * @param callback    The object containing external calls for requesting data and notifying of finish.
     * @throws IllegalArgumentException If any of the arguments is given null, the exception will be thrown.
     */
    public FileDownloadSession(FileInit initMessage, Callback callback) throws IllegalArgumentException {
        if (initMessage == null) {
            throw new IllegalArgumentException("The initial message object can not be null.");
        }
        if (callback == null) {
            throw new IllegalArgumentException("The callback object can not be null.");
        }

        this.initMessage = initMessage;
        this.running = true;

        this.bytes = new ArrayList<>();
        this.hashes = new ArrayList<>();
        this.chunkSizes = new ArrayList<>();

        this.callback = callback;

        // Calculate the chunk count, and each of their sizes
        long fullChunkDataBytes = CHUNK_SIZE - (PREVIOUS_HASH_SIZE + CURRENT_HASH_SIZE);
        long fullSizedChunks = initMessage.getFileSize() / fullChunkDataBytes;
        long leftoverSizedChunk = initMessage.getFileSize() % fullChunkDataBytes;

        // Append them all into the list
        for (int i = 0; i < fullSizedChunks; i++) {
            chunkSizes.add(CHUNK_SIZE);
        }

        if (leftoverSizedChunk > 0) {
            chunkSizes.add((int) (leftoverSizedChunk + (PREVIOUS_HASH_SIZE + CURRENT_HASH_SIZE)));
        }

        // Request the first chunk
        executor.submit(() ->
                callback.sendRequest(initMessage.getFileName(), currentChunk, chunkSizes.get(currentChunk)));
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isSuccess() {
        return success;
    }

    public FileInit getInitMessage() {
        return initMessage;
    }

    public FileTransferStatus getStatus() {
        return status;
    }

    public FileTransferError getError() {
        return error;
    }

    public List<Byte> getBytes() {
        return bytes;
    }

    /**
     * This is the method used to receive the external message that this file transfer needs to be aborted.
     * This will set the state of the session to aborted, notify the external of this state,
     * and stop receiving any data, and fold everything.
     *
     * @return Whether the session was successfully aborted. If it wasn't running, it will not be aborted. If it was
     * successful, it will not be aborted, and if it had already thrown an error, it will not be aborted.
     */
    public synchronized boolean abort() {
        // If the session is not running
        if (!this.running)
            return false;
        // If the session was successful
        if (this.success)
            return false;
        // If the session has thrown an error
        if (!this.aborted)
            return false;

        // Set the state for aborted
        this.running = false;
        this.success = false;
        this.aborted = true;

        // Reset the values
        currentChunk = 0;
        bytes.clear();

        status = getCurrentStatus();
        error = null;

        // Call the callback
        executor.submit(() -> callback.onFinish(status, null));

        return true;
    }

    /**
     * This is the method used to receive data from the response after this session sent a request for a specific chunk.
     * In here, the payload is analyzed to verify that it is valid, and if it is not, to retry to obtain a chunk,
     * or reset the entire process if necessary.
     *
     * @param receivedBytes Entire payload from response, containing 32 bytes for previous chunk hash,
     *                      bytes from current chunk, and additional 32 bytes for the current chunk hash.
     * @return Whether this specific chunk processing was successful. The session will itself request the chunk again if
     * it decides that is something it wants to do, because it may be over the limits, and will want to report
     * an error, since it had retried too many times.
     */
    public synchronized boolean receiveBytes(byte[] receivedBytes) {
        LOG.trace("Received chunk of bytes. Size of chunk: " + receivedBytes.length +
                ", current chunk count: " + currentChunk + ".");

        // Check the session status
        if (!running)
            throw new IllegalStateException("This session is not running anymore, it is not accepting chunks.");

        // Check the array size
        if (receivedBytes.length < MINIMUM_PACKET_SIZE)
            throw new IllegalArgumentException("The passed bytes is not a valid chunk message.");
        if (receivedBytes.length != chunkSizes.get(currentChunk))
            throw new IllegalArgumentException("The passed bytes is not the same size as requested.");

        // Obtain the previous hash
        byte[] previousHash = Arrays.copyOfRange(receivedBytes, 0, 32);
        byte[] chunkData = Arrays.copyOfRange(receivedBytes, 32, receivedBytes.length - 32);
        byte[] currentHash = Arrays.copyOfRange(receivedBytes, receivedBytes.length - 32, receivedBytes.length);

        // Analyze the chunk received.
        // Analyze the first hash to be all zeroes.
        if (currentChunk == 0) {
            for (byte hashByte : previousHash) {
                if (hashByte != 0) {
                    Log.warn("Invalid header for first chunk, previous hash is not 0.");
                    return requestChunkAgain();
                }
            }
        }

        // Append all the chunk data into the bytes
        for (byte chunkByte : chunkData) {
            bytes.add(chunkByte);
        }

        // Check if the file is fully here now.
        if (currentChunk == chunkSizes.size() && initMessage.getFileSize() == bytes.size()) {
            // If the entire file hash is invalid, restart the entire process
            if (!Arrays.equals(calculateHashForBytes(bytes), Base64.decodeBase64(initMessage.getFileHash()))) {
                return restartDataObtain();
            }

            // Return everything
            status = getCurrentStatus();
            error = null;
            executor.submit(() -> callback.onFinish(status, error));
            return true;
        }

        executor.submit(() ->
                callback.sendRequest(initMessage.getFileName(), ++currentChunk, chunkSizes.get(currentChunk)));
        return true;
    }

    /**
     * This is an internal method used to define how a chunk of bytes is hashed.
     *
     * @param data Input bytes to be calculated a SHA256 hash from.
     * @return The SHA256 hash of input data as byte array.
     */
    private byte[] calculateHashForBytes(List<Byte> data) {
        byte[] bytes = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            bytes[i] = data.get(i);
        }
        return DigestUtils.sha256(bytes);
    }

    /**
     * This is an internal method used to define how a chunk of bytes is hashed.
     *
     * @param data Input bytes to be calculated a SHA256 hash from.
     * @return The SHA256 hash of input data as byte array.
     */
    private byte[] calculateHashForBytes(byte[] data) {
        return DigestUtils.sha256(data);
    }

    /**
     * This is an internal method used to define how a chunk for which the current hash of previous chunk, and previous
     * hash of current chunk are not equal.
     */
    private boolean requestPreviousChunk() {
        Log.debug("Requesting the previous chunk.");

        // If we already requested previous chunks over the limit, restart the process.
        if (chunkRetryCount == MAX_RETRY) {
            Log.warn("Previous chunks have been re-requested " + chunkRetryCount +
                    " times, achieving the limit. Restarting the process.");
            restartDataObtain();
            return false;
        }

        // Filter out this invalid state.
        if (currentChunk == 0) {
            throw new IllegalStateException("Request previous chunk called when current chunk is first chunk.");
        }

        // Return a chunk back, remove the hash, and delete the bytes
        --currentChunk;
        hashes.remove(currentChunk);
        for (int i = 0; i < chunkSizes.get(currentChunk); i++) {
            bytes.remove(bytes.size() - 1);
        }

        // Increment the counter, and request the chunk again
        ++chunkRetryCount;
        executor.submit(() ->
                callback.sendRequest(initMessage.getFileName(), currentChunk, chunkSizes.get(currentChunk)));
        return true;
    }

    /**
     * This is an internal method used to define how a chunk for which the current hash is invalid, will be re-obtained.
     */
    private boolean requestChunkAgain() {
        Log.debug("Requesting a chunk again.");

        // If we already requested the chunk over the limit, restart the process
        if (chunkRetryCount == MAX_RETRY) {
            Log.warn("A single chunk has been re-requested " + chunkRetryCount +
                    " times, achieving the limit. Restarting the process.");
            restartDataObtain();
            return false;
        }

        // Increment the counter, and request the chunk again
        ++chunkRetryCount;
        executor.submit(() ->
                callback.sendRequest(initMessage.getFileName(), currentChunk, chunkSizes.get(currentChunk)));
        return true;
    }

    /**
     * This is an internal method used to define how the entire session will be restarted after the chunk reacquire has
     * been called to the limit.
     */
    private boolean restartDataObtain() {
        Log.debug("Restarting the data obtain session.");

        // If we already restarted the file obtain too much times, set the state to error and notify
        if (restartCount == MAX_RESTART) {
            Log.warn("The session was restarted " + restartCount +
                    " times, achieving the limit. Returning error.");
            this.running = false;
            this.success = false;
            this.aborted = false;

            currentChunk = 0;
            bytes.clear();
            chunkSizes.clear();
            hashes.clear();

            status = getCurrentStatus();
            error = FileTransferError.RETRY_COUNT_EXCEEDED;

            executor.submit(() -> callback.onFinish(status, error));
            return false;
        }

        // Increment the counter, restart all the data
        ++restartCount;
        chunkRetryCount = 0;
        bytes.clear();
        hashes.clear();

        // Request the first chunk again
        Log.debug("Requesting first chunk after restart.");
        executor.submit(() ->
                callback.sendRequest(initMessage.getFileName(), currentChunk, chunkSizes.get(currentChunk)));
        return true;
    }

    /**
     * This is an internal method used to calculate based on the state, what is the current File Transfer Status.
     *
     * @return The transfer status described with `FileTransferStatus` enum value.
     */
    private FileTransferStatus getCurrentStatus() {
        if (this.running) {
            Log.debug("The session status now is '" + FileTransferStatus.FILE_TRANSFER.name() + "'.");
            return FileTransferStatus.FILE_TRANSFER;
        }
        if (this.aborted) {
            Log.debug("The session status now is '" + FileTransferStatus.ABORTED.name() + "'.");
            return FileTransferStatus.ABORTED;
        }
        if (this.success) {
            Log.debug("The session status now is '" + FileTransferStatus.FILE_READY.name() + "'.");
            return FileTransferStatus.FILE_READY;
        }
        Log.debug("The session status now is '" + FileTransferStatus.ERROR.name() + "'.");
        return FileTransferStatus.ERROR;
    }

    /**
     * This is the public Callback interface for this class. It contains two calls, sendRequest that should be routed
     * to send a message requesting the specified chunk of data, and onFinish that returns the result of work from this
     * session.
     */
    public interface Callback {
        void sendRequest(String fileName, int chunkIndex, int chunkSize);

        void onFinish(FileTransferStatus status, FileTransferError error);
    }
}