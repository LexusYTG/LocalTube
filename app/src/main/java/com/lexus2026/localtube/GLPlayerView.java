package com.lexus2026.localtube;

import android.content.*;
import android.graphics.*;
import android.opengl.*;
import android.util.*;
import android.view.*;
import java.nio.*;
import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.*;

import javax.microedition.khronos.egl.EGLConfig;

public class GLPlayerView extends GLSurfaceView {

    private static final String TAG = "GLPlayerView";

    public interface Listener {
        void onSurfaceReady(Surface surface);
        void onSurfaceDestroyed();
    }

    private final Renderer renderer;
    private Listener listener;

    public GLPlayerView(Context ctx) {
        super(ctx);
        setEGLContextClientVersion(2);
        renderer = new Renderer();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void setListener(Listener l) { listener = l; }

    public void setVideoSize(final int w, final int h) {
        queueEvent(new Runnable() {
                @Override public void run() {
                    renderer.videoW = w;
                    renderer.videoH = h;
                    requestRender();
                }
            });
    }

    // Conservados por compatibilidad con SeriesPlayerActivity u otras llamadas futuras — sin efecto.
    public void setSharpen(float amount, boolean bicubicIgnored) {}
    public void setUpscale(boolean enabled, int targetW, int targetH) {}

    private class Renderer implements GLSurfaceView.Renderer,
    SurfaceTexture.OnFrameAvailableListener {

        int videoW = 0;
        int videoH = 0;

        private int oesProg;
        private int oesAPosition, oesATexCoord, oesUTexMatrix;
        private int textureId;
        private SurfaceTexture surfaceTexture;
        private final float[] texMatrix = new float[16];
        private FloatBuffer quadVerts;
        private FloatBuffer quadUVs;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            if (surfaceTexture != null) {
                surfaceTexture.setOnFrameAvailableListener(null);
                surfaceTexture.release();
                surfaceTexture = null;
            }
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{ textureId }, 0);
                textureId = 0;
            }

            initOESProgram();
            initQuad();

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            textureId = tex[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                   GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                   GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                   GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                                   GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setOnFrameAvailableListener(this);

            final Surface surface = new Surface(surfaceTexture);
            post(new Runnable() {
                    @Override public void run() {
                        if (listener != null) listener.onSurfaceReady(surface);
                    }
                });
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (surfaceTexture == null) return;
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(texMatrix);

            int vw = getWidth();
            int vh = getHeight();

            int vpX = 0, vpY = 0, vpW = vw, vpH = vh;
            if (videoW > 0 && videoH > 0 && vw > 0 && vh > 0) {
                float vRatio = (float) videoW / videoH;
                float sRatio = (float) vw / vh;
                if (vRatio > sRatio) {
                    vpW = vw;
                    vpH = (int) (vw / vRatio);
                } else {
                    vpH = vh;
                    vpW = (int) (vh * vRatio);
                }
                vpX = (vw - vpW) / 2;
                vpY = (vh - vpH) / 2;
            }

            GLES20.glViewport(0, 0, vw, vh);
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glViewport(vpX, vpY, vpW, vpH);

            GLES20.glUseProgram(oesProg);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniformMatrix4fv(oesUTexMatrix, 1, false, texMatrix, 0);

            GLES20.glEnableVertexAttribArray(oesAPosition);
            GLES20.glVertexAttribPointer(oesAPosition, 2, GLES20.GL_FLOAT, false, 0, quadVerts);
            GLES20.glEnableVertexAttribArray(oesATexCoord);
            GLES20.glVertexAttribPointer(oesATexCoord, 2, GLES20.GL_FLOAT, false, 0, quadUVs);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(oesAPosition);
            GLES20.glDisableVertexAttribArray(oesATexCoord);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            requestRender();
        }

        private void initQuad() {
            float[] verts = { -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f };
            float[] uvs   = {  0f,  0f, 1f,  0f,  0f, 1f, 1f, 1f };
            quadVerts = toFloatBuffer(verts);
            quadUVs   = toFloatBuffer(uvs);
        }

        private FloatBuffer toFloatBuffer(float[] data) {
            ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer fb = bb.asFloatBuffer();
            fb.put(data);
            fb.position(0);
            return fb;
        }

        private void initOESProgram() {
            String vs =
                "attribute vec4 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "  gl_Position = aPosition;\n" +
                "  vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;\n" +
                "}\n";

            String fs =
                "#extension GL_OES_EGL_image_external : require\n" +
                "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                "precision highp float;\n" +
                "#else\n" +
                "precision mediump float;\n" +
                "#endif\n" +
                "uniform samplerExternalOES uTexture;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                "}\n";

            oesProg = buildProgram(vs, fs);
            oesAPosition  = GLES20.glGetAttribLocation(oesProg, "aPosition");
            oesATexCoord  = GLES20.glGetAttribLocation(oesProg, "aTexCoord");
            oesUTexMatrix = GLES20.glGetUniformLocation(oesProg, "uTexMatrix");
        }

        private int buildProgram(String vs, String fs) {
            int vsh = compileShader(GLES20.GL_VERTEX_SHADER, vs);
            int fsh = compileShader(GLES20.GL_FRAGMENT_SHADER, fs);
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vsh);
            GLES20.glAttachShader(prog, fsh);
            GLES20.glLinkProgram(prog);
            GLES20.glDeleteShader(vsh);
            GLES20.glDeleteShader(fsh);
            return prog;
        }

        private int compileShader(int type, String src) {
            int sh = GLES20.glCreateShader(type);
            GLES20.glShaderSource(sh, src);
            GLES20.glCompileShader(sh);
            int[] status = new int[1];
            GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                Log.e(TAG, "Shader error: " + GLES20.glGetShaderInfoLog(sh));
                GLES20.glDeleteShader(sh);
            }
            return sh;
        }
    }
}
