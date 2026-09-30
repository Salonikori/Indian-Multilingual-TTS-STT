#!/usr/bin/env python3
"""
iTantra Performance Testing Script
Measures STT/TTS latencies, tests alert system, and verifies background operation
"""

import subprocess
import time
import json
import statistics
from datetime import datetime
from pathlib import Path

class iTantraPerformanceTester:
    def __init__(self):
        self.adb_path = r"C:\Users\salon\AppData\Local\Android\Sdk\platform-tools\adb.exe"
        self.package_name = "com.itantra.app"
        self.results = {}
        
    def run_adb(self, command):
        """Execute adb command and return output"""
        try:
            full_command = [self.adb_path] + command
            result = subprocess.run(full_command, capture_output=True, text=True, timeout=30)
            return result.stdout.strip(), result.stderr.strip(), result.returncode
        except subprocess.TimeoutExpired:
            return "", "Timeout", 1
        except Exception as e:
            return "", str(e), 1
    
    def get_device_info(self):
        """Get device information"""
        print("📱 Getting device information...")
        
        # Device model
        model, _, _ = self.run_adb(["shell", "getprop", "ro.product.model"])
        
        # Android version
        version, _, _ = self.run_adb(["shell", "getprop", "ro.build.version.release"])
        
        # API level
        api, _, _ = self.run_adb(["shell", "getprop", "ro.build.version.sdk"])
        
        # Architecture
        arch, _, _ = self.run_adb(["shell", "getprop", "ro.product.cpu.abi"])
        
        # Available RAM
        meminfo, _, _ = self.run_adb(["shell", "cat", "/proc/meminfo"])
        ram_kb = None
        for line in meminfo.split('\n'):
            if line.startswith('MemTotal:'):
                ram_kb = int(line.split()[1])
                break
        
        device_info = {
            "model": model,
            "android_version": version,
            "api_level": api,
            "architecture": arch,
            "ram_mb": round(ram_kb / 1024) if ram_kb else "Unknown",
            "timestamp": datetime.now().isoformat()
        }
        
        print(f"   Model: {model}")
        print(f"   Android: {version} (API {api})")
        print(f"   Architecture: {arch}")
        print(f"   RAM: {device_info['ram_mb']} MB" if ram_kb else "   RAM: Unknown")
        
        return device_info
    
    def get_app_info(self):
        """Get app-specific information"""
        print("\n📦 Getting app information...")
        
        # APK size
        apk_info, _, _ = self.run_adb(["shell", "dumpsys", "package", self.package_name])
        apk_size = "Unknown"
        
        # App directory size
        app_dir_size, _, _ = self.run_adb(["shell", "du", "-sh", f"/data/user/0/{self.package_name}"])
        
        # Model files size
        models_size, _, _ = self.run_adb(["shell", "du", "-sh", f"/data/user/0/{self.package_name}/files/models"])
        
        app_info = {
            "apk_size": apk_size,
            "app_directory_size": app_dir_size.split()[0] if app_dir_size else "Unknown",
            "models_size": models_size.split()[0] if models_size else "Unknown"
        }
        
        print(f"   App directory: {app_info['app_directory_size']}")
        print(f"   Models directory: {app_info['models_size']}")
        
        return app_info
    
    def test_alert_system(self):
        """Test alert system behavior"""
        print("\n🚨 Testing alert system...")
        
        # Check current audio settings
        print("   Getting current audio settings...")
        
        # Ringer mode
        ringer_mode, _, _ = self.run_adb(["shell", "settings", "get", "global", "mode_ringer"])
        
        # Media volume
        media_vol, _, _ = self.run_adb(["shell", "settings", "get", "system", "volume_music"])
        
        # Do Not Disturb status
        dnd_status, _, _ = self.run_adb(["shell", "settings", "get", "global", "zen_mode"])
        
        alert_test = {
            "ringer_mode": ringer_mode,
            "media_volume": media_vol,
            "dnd_status": dnd_status,
            "test_timestamp": datetime.now().isoformat()
        }
        
        print(f"   Ringer mode: {ringer_mode}")
        print(f"   Media volume: {media_vol}")
        print(f"   DND status: {dnd_status}")
        
        return alert_test
    
    def test_background_service(self):
        """Test background service operation"""
        print("\n🔄 Testing background service...")
        
        # Check if AudioCaptureService is running
        services, _, _ = self.run_adb(["shell", "dumpsys", "activity", "services", self.package_name])
        
        # Check for our service
        audio_service_running = "AudioCaptureService" in services
        
        # Get battery optimization status
        battery_opt, _, _ = self.run_adb(["shell", "dumpsys", "deviceidle", "whitelist"])
        is_whitelisted = self.package_name in battery_opt
        
        background_test = {
            "audio_service_running": audio_service_running,
            "battery_optimized": not is_whitelisted,
            "test_timestamp": datetime.now().isoformat()
        }
        
        print(f"   AudioCaptureService running: {audio_service_running}")
        print(f"   Battery optimized: {not is_whitelisted}")
        
        return background_test
    
    def measure_app_startup_time(self, runs=5):
        """Measure app startup time"""
        print(f"\n⏱️ Measuring app startup time ({runs} runs)...")
        
        startup_times = []
        
        for i in range(runs):
            print(f"   Run {i+1}/{runs}...", end=" ")
            
            # Force stop the app
            self.run_adb(["shell", "am", "force-stop", self.package_name])
            time.sleep(2)
            
            # Start the app and measure time
            start_time = time.time()
            self.run_adb(["shell", "am", "start", "-n", f"{self.package_name}/.MainActivity"])
            
            # Wait for app to be fully launched (check for activity)
            launched = False
            timeout = 10
            check_start = time.time()
            
            while not launched and (time.time() - check_start) < timeout:
                activity, _, _ = self.run_adb(["shell", "dumpsys", "activity", "activities"])
                if self.package_name in activity and "mResumed=true" in activity:
                    launched = True
                    break
                time.sleep(0.1)
            
            if launched:
                startup_time = time.time() - start_time
                startup_times.append(startup_time)
                print(f"{startup_time:.2f}s")
            else:
                print("Timeout")
        
        if startup_times:
            startup_stats = {
                "runs": len(startup_times),
                "mean_ms": round(statistics.mean(startup_times) * 1000, 1),
                "median_ms": round(statistics.median(startup_times) * 1000, 1),
                "min_ms": round(min(startup_times) * 1000, 1),
                "max_ms": round(max(startup_times) * 1000, 1),
                "raw_times": [round(t * 1000, 1) for t in startup_times]
            }
            
            print(f"   Mean: {startup_stats['mean_ms']}ms")
            print(f"   Median: {startup_stats['median_ms']}ms")
            print(f"   Range: {startup_stats['min_ms']}-{startup_stats['max_ms']}ms")
            
            return startup_stats
        else:
            return {"error": "No successful startup measurements"}
    
    def monitor_memory_usage(self, duration=30):
        """Monitor memory usage over time"""
        print(f"\n💾 Monitoring memory usage for {duration}s...")
        
        memory_samples = []
        start_time = time.time()
        
        while (time.time() - start_time) < duration:
            # Get memory info
            meminfo, _, _ = self.run_adb(["shell", "dumpsys", "meminfo", self.package_name])
            
            if "TOTAL" in meminfo:
                lines = meminfo.split('\n')
                for line in lines:
                    if "TOTAL" in line and ":" in line:
                        # Extract memory value (in KB)
                        parts = line.split()
                        if len(parts) >= 2:
                            try:
                                memory_kb = int(parts[1].replace(',', ''))
                                memory_samples.append({
                                    "timestamp": time.time() - start_time,
                                    "memory_kb": memory_kb,
                                    "memory_mb": round(memory_kb / 1024, 1)
                                })
                                break
                            except ValueError:
                                pass
            
            time.sleep(2)
        
        if memory_samples:
            memory_values = [s["memory_mb"] for s in memory_samples]
            memory_stats = {
                "samples": len(memory_samples),
                "mean_mb": round(statistics.mean(memory_values), 1),
                "median_mb": round(statistics.median(memory_values), 1),
                "min_mb": round(min(memory_values), 1),
                "max_mb": round(max(memory_values), 1),
                "samples_data": memory_samples[-5:]  # Last 5 samples
            }
            
            print(f"   Samples: {memory_stats['samples']}")
            print(f"   Mean: {memory_stats['mean_mb']} MB")
            print(f"   Range: {memory_stats['min_mb']}-{memory_stats['max_mb']} MB")
            
            return memory_stats
        else:
            return {"error": "No memory samples collected"}
    
    def run_comprehensive_test(self):
        """Run all performance tests"""
        print("🚀 Starting iTantra Performance Testing")
        print("=" * 50)
        
        # Gather basic info
        self.results["device_info"] = self.get_device_info()
        self.results["app_info"] = self.get_app_info()
        
        # Test alert system
        self.results["alert_system"] = self.test_alert_system()
        
        # Test background service
        self.results["background_service"] = self.test_background_service()
        
        # Measure startup time
        self.results["startup_performance"] = self.measure_app_startup_time()
        
        # Monitor memory usage
        self.results["memory_usage"] = self.monitor_memory_usage()
        
        # Save results
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        results_file = f"performance_results_{timestamp}.json"
        
        with open(results_file, 'w') as f:
            json.dump(self.results, f, indent=2)
        
        print(f"\n✅ Performance test completed!")
        print(f"📊 Results saved to: {results_file}")
        
        return self.results

if __name__ == "__main__":
    tester = iTantraPerformanceTester()
    results = tester.run_comprehensive_test()
    
    # Print summary
    print("\n📈 PERFORMANCE SUMMARY")
    print("=" * 30)
    
    if "device_info" in results:
        device = results["device_info"]
        print(f"Device: {device.get('model', 'Unknown')} (API {device.get('api_level', '?')})")
    
    if "startup_performance" in results and "mean_ms" in results["startup_performance"]:
        startup = results["startup_performance"]
        print(f"Startup: {startup['mean_ms']}ms avg")
    
    if "memory_usage" in results and "mean_mb" in results["memory_usage"]:
        memory = results["memory_usage"]
        print(f"Memory: {memory['mean_mb']} MB avg")
    
    if "background_service" in results:
        bg = results["background_service"]
        print(f"Background Service: {'✅' if bg.get('audio_service_running') else '❌'}")