// This file is generated. 
// Edit platform/darwin/scripts/generate-style-code.js, then run `make style-code-darwin`.

#import "MGLStyleLayerTests.h"

@interface MGLRasterLayerTests : MGLStyleLayerTests
@end

@implementation MGLRasterLayerTests

- (void)testRasterLayer {
    NSString *filePath = [[NSBundle bundleForClass:self.class] pathForResource:@"amsterdam" ofType:@"geojson"];
    NSURL *url = [NSURL fileURLWithPath:filePath];
    MGLGeoJSONSource *source = [[MGLGeoJSONSource alloc] initWithIdentifier:@"sourceID" URL:url options:nil];
    [self.mapView.style addSource:source];
    MGLRasterStyleLayer *layer = [[MGLRasterStyleLayer alloc] initWithIdentifier:@"layerID" source:source];
    [self.mapView.style addLayer:layer];

    layer.rasterOpacity = [MGLRuntimeStylingHelper testNumber];
    layer.rasterHueRotate = [MGLRuntimeStylingHelper testNumber];
    layer.rasterSaturation = [MGLRuntimeStylingHelper testNumber];
    layer.rasterContrast = [MGLRuntimeStylingHelper testNumber];
    layer.rasterFadeDuration = [MGLRuntimeStylingHelper testNumber];
    layer.rasterBrightnessMinimum = [MGLRuntimeStylingHelper testNumber];
    layer.rasterBrightnessMaximum = [MGLRuntimeStylingHelper testNumber];

    MGLRasterStyleLayer *gLayer = (MGLRasterStyleLayer *)[self.mapView.style layerWithIdentifier:@"layerID"];
    XCTAssertTrue([gLayer isKindOfClass:[MGLRasterStyleLayer class]]);
    XCTAssertEqualObjects(gLayer.rasterOpacity, [MGLRuntimeStylingHelper testNumber]);
    XCTAssertEqualObjects(gLayer.rasterHueRotate, [MGLRuntimeStylingHelper testNumber]);
    XCTAssertEqualObjects(gLayer.rasterSaturation, [MGLRuntimeStylingHelper testNumber]);
    XCTAssertEqualObjects(gLayer.rasterContrast, [MGLRuntimeStylingHelper testNumber]);
    XCTAssertEqualObjects(gLayer.rasterFadeDuration, [MGLRuntimeStylingHelper testNumber]);
    XCTAssertEqualObjects(gLayer.rasterBrightnessMinimum, [MGLRuntimeStylingHelper testNumber]);
    XCTAssertEqualObjects(gLayer.rasterBrightnessMaximum, [MGLRuntimeStylingHelper testNumber]);

    layer.rasterOpacity = [MGLRuntimeStylingHelper testNumberFunction];
    layer.rasterHueRotate = [MGLRuntimeStylingHelper testNumberFunction];
    layer.rasterSaturation = [MGLRuntimeStylingHelper testNumberFunction];
    layer.rasterContrast = [MGLRuntimeStylingHelper testNumberFunction];
    layer.rasterFadeDuration = [MGLRuntimeStylingHelper testNumberFunction];
    layer.rasterBrightnessMinimum = [MGLRuntimeStylingHelper testNumberFunction];
    layer.rasterBrightnessMaximum = [MGLRuntimeStylingHelper testNumberFunction];

    XCTAssertEqualObjects(gLayer.rasterOpacity, [MGLRuntimeStylingHelper testNumberFunction]);
    XCTAssertEqualObjects(gLayer.rasterHueRotate, [MGLRuntimeStylingHelper testNumberFunction]);
    XCTAssertEqualObjects(gLayer.rasterSaturation, [MGLRuntimeStylingHelper testNumberFunction]);
    XCTAssertEqualObjects(gLayer.rasterContrast, [MGLRuntimeStylingHelper testNumberFunction]);
    XCTAssertEqualObjects(gLayer.rasterFadeDuration, [MGLRuntimeStylingHelper testNumberFunction]);
    XCTAssertEqualObjects(gLayer.rasterBrightnessMinimum, [MGLRuntimeStylingHelper testNumberFunction]);
    XCTAssertEqualObjects(gLayer.rasterBrightnessMaximum, [MGLRuntimeStylingHelper testNumberFunction]);
}

@end
