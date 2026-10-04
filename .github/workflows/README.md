# GitHub Actions CI/CD for iTantra

This directory contains GitHub Actions workflows for building and distributing the iTantra Android APK.

## Workflows

### `build.yml` - Main Build Pipeline

Automatically builds debug APK on push/PR with the following features:

- **Triggers:**
  - Push to `main` or `develop` branches
  - Pull requests to `main` branch  
  - Manual workflow dispatch

- **Build Process:**
  1. Downloads real model files (when available)
  2. Bundles models in assets/
  3. Builds debug APK with Gradle
  4. Creates versioned APK filename
  5. Uploads APK as artifact

- **Artifacts:**
  - `iTantra-APK` - Contains the built APK and build info
  - `build-logs` - Build logs (only on failure)

## Using the Built APK

### Download from GitHub Actions

1. Go to the [Actions tab](../../actions) of this repository
2. Click on the latest successful build 
3. Download the "iTantra-APK" artifact
4. Extract the ZIP file to get the APK

### Installation Requirements

- Android device with API 26+ (Android 8.0+)
- arm64-v8a architecture (modern Android phones)
- ~200MB free space (for models)
- Microphone and Bluetooth permissions

### First-Time Setup

1. Install APK (enable "Unknown sources" in Android settings)
2. Open iTantra app
3. Grant permissions when prompted:
   - Microphone access
   - Bluetooth access  
   - Notifications (Android 13+)
4. Select language (Hindi/English)
5. Tap "Install & Load" to install models from assets
6. Wait for model installation to complete

### Using the App

1. **Language Selection:** Choose Hindi or English
2. **Model Loading:** Tap "Load" after installation
3. **Communication Mode:** Toggle between PTT and Phone mode
4. **Bluetooth Setup:** Use "Setup" to connect devices
5. **Push-to-Talk:** Hold PTT button to transmit in PTT mode

## Model Management

The workflow handles model downloading and bundling:

- **Development:** Uses placeholder files (~40MB APK)
- **CI Build:** Downloads real models via `scripts/download_models.py`
- **Production APK:** Bundles full models (~200MB APK)

## Troubleshooting

### Build Failures

1. Check the build logs artifact
2. Verify sherpa-onnx JitPack dependency
3. Check model download script status
4. Ensure Gradle wrapper permissions

### APK Installation Issues

1. Verify Android version (API 26+)
2. Check device architecture (arm64-v8a required)
3. Ensure sufficient storage space
4. Grant all required permissions

### Model Loading Issues

1. Check app permissions
2. Verify models were bundled in APK
3. Clear app data and retry installation
4. Check logs for specific error messages

## Development

To modify the build process:

1. Edit `.github/workflows/build.yml`
2. Update model URLs in `scripts/download_models.py` 
3. Test changes on feature branch
4. Merge to main for production builds

## Security Notes

- APKs are debug builds (not for production distribution)
- No sensitive credentials stored in workflow
- Models downloaded from public sources only
- All artifacts have 30-day retention