package de.k3b.android.lossless_jpg_crop;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;

import com.facebook.spectrum.Configuration;
import com.facebook.spectrum.EncodedImageSink;
import com.facebook.spectrum.EncodedImageSource;
import com.facebook.spectrum.Spectrum;
import com.facebook.spectrum.SpectrumSoLoader;
import com.facebook.spectrum.image.EncodedImageFormat;
import com.facebook.spectrum.logging.SpectrumLogcatLogger;
import com.facebook.spectrum.options.TranscodeOptions;
import com.facebook.spectrum.plugins.SpectrumPluginJpeg;
import com.facebook.spectrum.requirements.EncodeRequirement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class ImageProcessor {
    private final Spectrum mSpectrum;

    public static void init(Context context) {
        SpectrumSoLoader.init(context);
    }

    public ImageProcessor() {
        mSpectrum = Spectrum.make(
                new SpectrumLogcatLogger(Log.INFO),
                Configuration.makeEmpty(),
                SpectrumPluginJpeg.get()); // JPEG only
        // DefaultPlugins.get()); // JPEG, PNG and WebP plugins
    }

    public void crop(InputStream inputStream, OutputStream outputStream, Rect rect, int relativeRotationInDegrees) throws IOException {
        crop(inputStream, outputStream, rect.left, rect.top, rect.right, rect.bottom, relativeRotationInDegrees);
    }

    public void crop(InputStream inputStream, OutputStream outputStream, int left, int top, int right, int bottom, int relativeRotationInDegrees) throws IOException {
        final EncodeRequirement encoding =
                new EncodeRequirement(EncodedImageFormat.JPEG, 80, EncodeRequirement.Mode.LOSSLESS);
        try {
            final TranscodeOptions.Builder optionsBuilder = TranscodeOptions
                    .Builder(encoding)
                    .cropAbsoluteToOrigin(left, top, right, bottom, false);
            if (relativeRotationInDegrees != 0) {
                optionsBuilder.rotate(relativeRotationInDegrees);
            }
            mSpectrum.transcode(
                    EncodedImageSource.from(inputStream),
                    EncodedImageSink.from(outputStream),
                    optionsBuilder.build(),
                    "my_callsite_identifier");
        } catch (Exception ex) {
            throw new IOException("Cannot Transcode from " + inputStream + " to " + outputStream + " : " + ex.getMessage(), ex);
        }
    }
}
