add_definitions(-DMBGL_USE_GLES2=1)

include(cmake/test-files.cmake)

#Include to use build specific variables
include(${CMAKE_CURRENT_BINARY_DIR}/toolchain.cmake)

# Build thin archives.
set(CMAKE_CXX_ARCHIVE_CREATE "<CMAKE_AR> cruT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_CREATE "<CMAKE_AR> cruT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_CXX_ARCHIVE_APPEND "<CMAKE_AR> ruT <TARGET> <LINK_FLAGS> <OBJECTS>")
set(CMAKE_C_ARCHIVE_APPEND "<CMAKE_AR> ruT <TARGET> <LINK_FLAGS> <OBJECTS>")

if ((ANDROID_ABI STREQUAL "armeabi") OR (ANDROID_ABI STREQUAL "armeabi-v7a") OR (ANDROID_ABI STREQUAL "arm64-v8a") OR
    (ANDROID_ABI STREQUAL "x86") OR (ANDROID_ABI STREQUAL "x86_64"))
    # Use Identical Code Folding on platforms that support the gold linker.
    set(CMAKE_EXE_LINKER_FLAGS "-fuse-ld=gold -Wl,--icf=safe ${CMAKE_EXE_LINKER_FLAGS}")
    set(CMAKE_SHARED_LINKER_FLAGS "-fuse-ld=gold -Wl,--icf=safe ${CMAKE_SHARED_LINKER_FLAGS}")
endif()

mason_use(jni.hpp VERSION 2.0.0 HEADER_ONLY)
mason_use(libjpeg-turbo VERSION 1.5.0)
mason_use(libpng VERSION 1.6.25)
mason_use(libzip VERSION 1.1.3)
mason_use(nunicode VERSION 1.7.1)
mason_use(sqlite VERSION 3.14.2)
mason_use(gtest VERSION 1.7.0)

set(ANDROID_SDK_PROJECT_DIR ${CMAKE_SOURCE_DIR}/platform/android/MapboxGLAndroidSDK)
set(ANDROID_JNI_TARGET_DIR ${ANDROID_SDK_PROJECT_DIR}/src/main/jniLibs/${ANDROID_JNIDIR})
set(ANDROID_ASSETS_TARGET_DIR ${ANDROID_SDK_PROJECT_DIR}/src/main/assets)
set(ANDROID_TEST_APP_JNI_TARGET_DIR ${CMAKE_SOURCE_DIR}/platform/android/MapboxGLAndroidSDKTestApp/src/main/jniLibs/${ANDROID_JNIDIR})

macro(mbgl_android_copy_asset source target)
    add_custom_command(
        OUTPUT ${ANDROID_ASSETS_TARGET_DIR}/${target}
        COMMAND  ${CMAKE_COMMAND} -E copy ${CMAKE_SOURCE_DIR}/${source} ${ANDROID_ASSETS_TARGET_DIR}/${target}
        DEPENDS ${CMAKE_SOURCE_DIR}/${source}
    )
endmacro()

mbgl_android_copy_asset(common/ca-bundle.crt ca-bundle.crt)

add_custom_target(mbgl-copy-android-assets
    DEPENDS ${ANDROID_ASSETS_TARGET_DIR}/ca-bundle.crt
)

## mbgl core ##

macro(mbgl_platform_core)

    target_sources(mbgl-core
        # Loop
        PRIVATE platform/android/src/thread.cpp
        PRIVATE platform/android/src/async_task.cpp
        PRIVATE platform/android/src/run_loop.cpp
        PRIVATE platform/android/src/run_loop_impl.hpp
        PRIVATE platform/android/src/timer.cpp

        # File source
        PRIVATE platform/android/src/asset_file_source.cpp
        PRIVATE platform/android/src/http_file_source.cpp
        PRIVATE platform/default/default_file_source.cpp
        PRIVATE platform/default/local_file_source.cpp
        PRIVATE platform/default/online_file_source.cpp

        # Offline
        # PRIVATE include/mbgl/storage/offline.hpp
        PRIVATE platform/default/mbgl/storage/offline.cpp
        PRIVATE platform/default/mbgl/storage/offline_database.cpp
        PRIVATE platform/default/mbgl/storage/offline_database.hpp
        PRIVATE platform/default/mbgl/storage/offline_download.cpp
        PRIVATE platform/default/mbgl/storage/offline_download.hpp
        PRIVATE platform/default/sqlite3.cpp
        PRIVATE platform/default/sqlite3.hpp

        # Misc
        PRIVATE platform/android/src/logging_android.cpp
        PRIVATE platform/default/string_stdlib.cpp

        # Image handling
        PRIVATE platform/default/image.cpp
        PRIVATE platform/default/png_reader.cpp
        PRIVATE platform/default/jpeg_reader.cpp

        # Thread pool
        PRIVATE platform/default/mbgl/util/default_thread_pool.cpp
        PRIVATE platform/default/mbgl/util/default_thread_pool.hpp

        # Conversion C++ -> Java
        platform/android/src/conversion/constant.hpp
        platform/android/src/conversion/conversion.hpp
        platform/android/src/style/conversion/function.hpp
        platform/android/src/style/conversion/property_value.hpp
        platform/android/src/style/conversion/types.hpp
        platform/android/src/style/conversion/types_string_values.hpp

        # Style conversion Java -> C++
        platform/android/src/style/android_conversion.hpp
        platform/android/src/style/conversion/geojson.hpp
        platform/android/src/style/value.cpp
        platform/android/src/style/value.hpp
        platform/android/src/style/conversion/url_or_tileset.hpp

        # Style
        platform/android/src/style/layers/background_layer.cpp
        platform/android/src/style/layers/background_layer.hpp
        platform/android/src/style/layers/circle_layer.cpp
        platform/android/src/style/layers/circle_layer.hpp
        platform/android/src/style/layers/custom_layer.cpp
        platform/android/src/style/layers/custom_layer.hpp
        platform/android/src/style/layers/fill_layer.cpp
        platform/android/src/style/layers/fill_layer.hpp
        platform/android/src/style/layers/layer.cpp
        platform/android/src/style/layers/layer.hpp
        platform/android/src/style/layers/layers.cpp
        platform/android/src/style/layers/layers.hpp
        platform/android/src/style/layers/line_layer.cpp
        platform/android/src/style/layers/line_layer.hpp
        platform/android/src/style/layers/raster_layer.cpp
        platform/android/src/style/layers/raster_layer.hpp
        platform/android/src/style/layers/symbol_layer.cpp
        platform/android/src/style/layers/symbol_layer.hpp
        platform/android/src/style/sources/geojson_source.cpp
        platform/android/src/style/sources/geojson_source.hpp
        platform/android/src/style/sources/source.cpp
        platform/android/src/style/sources/source.hpp
        platform/android/src/style/sources/sources.cpp
        platform/android/src/style/sources/sources.hpp
        platform/android/src/style/sources/raster_source.cpp
        platform/android/src/style/sources/raster_source.hpp
        platform/android/src/style/sources/vector_source.cpp
        platform/android/src/style/sources/vector_source.hpp

        # Connectivity
        platform/android/src/connectivity_listener.cpp
        platform/android/src/connectivity_listener.hpp

        # Native map
        platform/android/src/native_map_view.cpp
        platform/android/src/native_map_view.hpp

        # Main jni bindings
        platform/android/src/attach_env.cpp
        platform/android/src/attach_env.hpp
        platform/android/src/java_types.cpp
        platform/android/src/java_types.hpp

        # Main entry point
        platform/android/src/jni.hpp
        platform/android/src/jni.cpp
    )

    target_include_directories(mbgl-core
        PUBLIC platform/default
    )

    target_add_mason_package(mbgl-core PUBLIC sqlite)
    target_add_mason_package(mbgl-core PUBLIC nunicode)
    target_add_mason_package(mbgl-core PUBLIC libpng)
    target_add_mason_package(mbgl-core PUBLIC libjpeg-turbo)
    target_add_mason_package(mbgl-core PUBLIC libzip)
    target_add_mason_package(mbgl-core PUBLIC geojson)
    target_add_mason_package(mbgl-core PUBLIC jni.hpp)
    target_add_mason_package(mbgl-core PUBLIC rapidjson)

    target_compile_options(mbgl-core
        PRIVATE -fvisibility=hidden
        PRIVATE -ffunction-sections
        PRIVATE -fdata-sections
        PRIVATE -Os
    )

    target_link_libraries(mbgl-core
        PUBLIC -llog
        PUBLIC -landroid
        PUBLIC -lEGL
        PUBLIC -lGLESv2
        PUBLIC -lstdc++
        PUBLIC -latomic
        PUBLIC -lz
        PUBLIC -Wl,--gc-sections
    )
endmacro()

## Main library ##

add_library(mapbox-gl SHARED
    platform/android/src/main.cpp
)

add_dependencies(mapbox-gl
    mbgl-copy-android-assets
)

target_compile_options(mapbox-gl
    PRIVATE -fvisibility=hidden
    PRIVATE -ffunction-sections
    PRIVATE -fdata-sections
    PRIVATE -Os
)

target_link_libraries(mapbox-gl
    PUBLIC mbgl-core
    PUBLIC -Wl,--gc-sections
)

# Create a stripped version of the library and copy it to the JNIDIR.
add_custom_command(TARGET mapbox-gl POST_BUILD
                   COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_JNI_TARGET_DIR}
                   COMMAND ${STRIP_COMMAND} $<TARGET_FILE:mapbox-gl> -o ${ANDROID_JNI_TARGET_DIR}/$<TARGET_FILE_NAME:mapbox-gl>)

## Test library ##

add_library(mbgl-test SHARED
    # Actual tests
    ${MBGL_TEST_FILES}

    # Main test entry point
    platform/android/src/test/main.jni.cpp

)

add_dependencies(mbgl-test
    mapbox-gl
)

target_sources(mbgl-test
    # Headless view
    PRIVATE platform/default/mbgl/gl/headless_backend.cpp
    PRIVATE platform/default/mbgl/gl/headless_backend.hpp
    PRIVATE platform/default/mbgl/gl/offscreen_view.cpp
    PRIVATE platform/default/mbgl/gl/offscreen_view.hpp

    PRIVATE platform/linux/src/headless_backend_egl.cpp
    PRIVATE platform/linux/src/headless_display_egl.cpp
)

target_compile_options(mbgl-test
    PRIVATE -fvisibility=hidden
    PRIVATE -Os
)

target_compile_definitions(mbgl-test
    PRIVATE MBGL_ASSET_ZIP=1
)

target_include_directories(mbgl-test
    PRIVATE include
    PRIVATE src # TODO: eliminate
    PRIVATE test/include
    PRIVATE test/src
    PRIVATE platform/default
    PRIVATE ${MBGL_GENERATED}/include
)

target_link_libraries(mbgl-test
    PRIVATE mbgl-core
)

target_add_mason_package(mbgl-test PRIVATE geometry)
target_add_mason_package(mbgl-test PRIVATE variant)
target_add_mason_package(mbgl-test PRIVATE unique_resource)
target_add_mason_package(mbgl-test PRIVATE rapidjson)
target_add_mason_package(mbgl-test PRIVATE gtest)
target_add_mason_package(mbgl-test PRIVATE pixelmatch)
target_add_mason_package(mbgl-test PRIVATE boost)
target_add_mason_package(mbgl-test PRIVATE geojson)
target_add_mason_package(mbgl-test PRIVATE geojsonvt)

add_custom_command(TARGET mbgl-test POST_BUILD
                   COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_CURRENT_BINARY_DIR}/stripped
                   COMMAND ${STRIP_COMMAND} $<TARGET_FILE:mapbox-gl> -o ${CMAKE_CURRENT_BINARY_DIR}/stripped/$<TARGET_FILE_NAME:mapbox-gl>
                   COMMAND ${STRIP_COMMAND} $<TARGET_FILE:mbgl-test> -o ${CMAKE_CURRENT_BINARY_DIR}/stripped/$<TARGET_FILE_NAME:mbgl-test>)

## Custom layer example ##

add_library(example-custom-layer SHARED
    platform/android/src/example_custom_layer.cpp
)

target_compile_options(example-custom-layer
    PRIVATE -fvisibility=hidden
    PRIVATE -ffunction-sections
    PRIVATE -fdata-sections
    PRIVATE -Os
)

target_link_libraries(example-custom-layer
    PRIVATE mapbox-gl
    PUBLIC -Wl,--gc-sections
)

add_custom_command(TARGET example-custom-layer POST_BUILD
                   COMMAND ${CMAKE_COMMAND} -E make_directory ${ANDROID_TEST_APP_JNI_TARGET_DIR}
                   COMMAND ${STRIP_COMMAND} $<TARGET_FILE:example-custom-layer> -o ${ANDROID_TEST_APP_JNI_TARGET_DIR}/$<TARGET_FILE_NAME:example-custom-layer>)
