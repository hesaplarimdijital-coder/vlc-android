// S-709: the system's Vulkan loader for ggml's Vulkan backend, opened when a job asks for the GPU.
//
// libnagmedar_lab.so does not link libvulkan.so: it loads on every device, Vulkan or not, and an
// Android 8 loader (Vulkan 1.0) lacks functions the backend calls. ggml reaches every Vulkan
// function through vkGetInstanceProcAddr but for these four, which it calls by name: they are
// defined here and go to the loader opened below. whisper_engine.cpp asks the GPU only after
// nagmedar_vulkan_load said yes, so they are never called without it.
#include <dlfcn.h>
#include <stddef.h>
#include <stdio.h>

#include <vulkan/vulkan_core.h>

static PFN_vkGetInstanceProcAddr instance_proc;
static PFN_vkGetDeviceProcAddr device_proc;
static PFN_vkGetPhysicalDeviceFeatures2 features2;
static PFN_vkCmdCopyBuffer copy_buffer;

/** 1 when the system's Vulkan loader is there and speaks Vulkan 1.2 (what ggml asks), else 0 and why in [why]. Not thread-safe: whisper_engine.cpp calls it under its lock. */
int nagmedar_vulkan_load(char *why, size_t size)
{
    if (instance_proc)
        return 1;
    void *lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (!lib)
        lib = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (!lib) {
        snprintf(why, size, "no Vulkan loader");
        return 0;
    }
    PFN_vkGetInstanceProcAddr gipa = (PFN_vkGetInstanceProcAddr)dlsym(lib, "vkGetInstanceProcAddr");
    PFN_vkEnumerateInstanceVersion version = gipa ? (PFN_vkEnumerateInstanceVersion)gipa(NULL, "vkEnumerateInstanceVersion") : NULL;
    PFN_vkGetDeviceProcAddr gdpa = (PFN_vkGetDeviceProcAddr)dlsym(lib, "vkGetDeviceProcAddr");
    PFN_vkGetPhysicalDeviceFeatures2 f2 = (PFN_vkGetPhysicalDeviceFeatures2)dlsym(lib, "vkGetPhysicalDeviceFeatures2");
    PFN_vkCmdCopyBuffer copy = (PFN_vkCmdCopyBuffer)dlsym(lib, "vkCmdCopyBuffer");
    uint32_t api = 0;
    if (!gipa || !version || !gdpa || !f2 || !copy) {
        snprintf(why, size, "a Vulkan 1.0 loader");
    } else if (version(&api) != VK_SUCCESS || api < VK_API_VERSION_1_2) {
        snprintf(why, size, "a Vulkan %u.%u loader", VK_API_VERSION_MAJOR(api), VK_API_VERSION_MINOR(api));
    } else {
        // kept open for the life of the process, as the backend is
        device_proc = gdpa;
        features2 = f2;
        copy_buffer = copy;
        instance_proc = gipa;
        return 1;
    }
    dlclose(lib);
    return 0;
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char *name)
{
    return instance_proc ? instance_proc(instance, name) : NULL;
}

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice device, const char *name)
{
    return device_proc ? device_proc(device, name) : NULL;
}

VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceFeatures2(VkPhysicalDevice device, VkPhysicalDeviceFeatures2 *features)
{
    features2(device, features);
}

VKAPI_ATTR void VKAPI_CALL vkCmdCopyBuffer(VkCommandBuffer buffer, VkBuffer src, VkBuffer dst, uint32_t count, const VkBufferCopy *regions)
{
    copy_buffer(buffer, src, dst, count, regions);
}
