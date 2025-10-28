package com.example.realtime_obstacle_detection.ui.screens.initialConfigurations

/**
 * Enum class representing different versions of YOLO object detection models
 * used within the application.
 *
 * Each entry corresponds to a unique model variant with the following properties:
 * @param displayName A user-friendly name for displaying in the UI or logs.
 * @param modelFileName The name of the TFLite model file used for inference.
 * @param labelFileName The associated label file containing object class names.
 *
 * Groupings:
 * - YOLOv8 / YOLOv12: Refers to different versions of the YOLO model architecture.
 * - 15 vs. 18 Obstacles: Refers to the number of object classes in the detection dataset.
 * - FP16, FP32, INT8, Integer Quant: Represents different quantization and precision formats
 * to balance model size, speed, and accuracy.
 *
 * The `DEFAULT` model is set to `YOLO8_18_OBSTACLE_FP32BIT`, but can be customized as needed.
 */
enum class Models(
    val displayName: String,
    val modelFileName: String,
    val labelFileName: String
) {
    // YoloV8 - 18 Obstacles ------------------------------------------------------------------------
    YOLO8_18_OBSTACLE_FP16BIT(
        "18 Obstacles YOLOv8 FP16",
        "18Obstacles_yolov8_float16.tflite",
        "18Obstacles_labels.txt"
    ),
    YOLO8_18_OBSTACLE_FP32BIT(
        "18 Obstacles YOLOv8 FP32",
        "18Obstacles_yolov8_float32.tflite",
        "18Obstacles_labels.txt"
    ),
    YOLO8_18_OBSTACLE_FULL_INTEGER(
        "18 Obstacles YOLOv8 Full Integer Quant",
        "18Obstacles_yolov8_full_integer_quant.tflite",
        "18Obstacles_labels.txt"
    ),
    YOLO8_18_OBSTACLE_INT8(
        "18 Obstacles YOLOv8 INT8",
        "18Obstacles_yolov8_int8.tflite",
        "18Obstacles_labels.txt"
    ),
    YOLO8_18_OBSTACLE_INTEGER_QUANT(
        "18 Obstacles YOLOv8 Integer Quant",
        "18Obstacles_yolov8_integer_quant.tflite",
        "18Obstacles_labels.txt"
    ),

    // YoloV12 - 18 Obstacles -----------------------------------------------------------------------
    YOLO12_18_OBSTACLE_FP16BIT(
        "18 Obstacles YOLOv12 FP16",
        "18Obstacles_yolov12_float16.tflite",
        "18Obstacles_labels.txt"
    ),
    YOLO12_18_OBSTACLE_FP32BIT(
        "18 Obstacles YOLOv12 FP32",
        "18Obstacles_yolov12_float32.tflite",
        "18Obstacles_labels.txt"
    ),

    // EE_Backbone + Neck Variants (Base) -----------------------------------------------------------
    EE_BACKBONE_NECK_FP16(
        "EE Backbone + Neck FP16 (Base)",
        "EE_Backbone_Neck_float16.tflite",
        "18Obstacles_labels.txt"
    ),
    EE_BACKBONE_NECK_FP32(
        "EE Backbone + Neck FP32 (Base)",
        "EE_Backbone_Neck_float32.tflite",
        "18Obstacles_labels.txt"
    ),
    EE_BACKBONE_NECK_INT8(
        "EE Backbone + Neck INT8 (Base)",
        "EE_Backbone_Neck_int8.tflite",
        "18Obstacles_labels.txt"
    ),

    // EE Pruned Backbone + Neck Variants (New) -----------------------------------------------------
//    EE_NECK_PRUNED_FP16(
//        "EE Neck Pruned FP16",
//        "EE_backbone_neck_manual_pruned_float16.tflite",
//        "18Obstacles_labels.txt"
//    ),
//    EE_NECK_PRUNED_FP32(
//        "EE Neck Pruned FP32",
//        "EE_backbone_neck_manual_pruned_float32.tflite",
//        "18Obstacles_labels.txt"
//    ),
//    EE_NECK_PRUNED_INT8(
//        "EE Neck Pruned INT8",
//        "EE_backbone_neck_manual_pruned_int8.tflite",
//        "18Obstacles_labels.txt"
//    ),

    // EE_Backbone Only Variants (Base) -------------------------------------------------------------
    EE_BACKBONE_ONLY_FP16(
        "EE Backbone Only FP16 (Base)",
        "EE_Backbone_only_float16.tflite",
        "18Obstacles_labels.txt"
    ),
    EE_BACKBONE_ONLY_INT8(
        "EE Backbone Only INT8 (Base)",
        "EE_Backbone_only_int8.tflite",
        "18Obstacles_labels.txt"
    ),
    EE_BACKBONEONLY_FP32(
        "EE BackboneOnly FP32 (Base)",
        "EE_Backboneonly_float32.tflite",
        "18Obstacles_labels.txt"
    ),

    // EE Pruned Backbone Variants  --------------------------------------------------------
    EE_ONLY_PRUNED_FP16(
        "EE Pruned FP16",
        "EE_backbone_neck_pruned_float16.tflite",
        "18Obstacles_labels.txt"
    ),
    EE_ONLY_PRUNED_FP32(
        "EE Pruned FP32",
        "EE_backbone_neck_pruned_float32.tflite",
        "18Obstacles_labels.txt"
    ),
    EE_ONLY_PRUNED_INT8(
        "EE Pruned INT8",
        "EE_backbone_neck_pruned_int8.tflite",
        "18Obstacles_labels.txt"
    ),

    // Customized Pruned Variants (New) --------------------------------------------------------------
    CUSTOMIZED_PRUNED_FP16(
        "Customized Pruned FP16",
        "customized_without_EE_pruned_float16.tflite",
        "18Obstacles_labels.txt"
    ),
    CUSTOMIZED_PRUNED_FP32(
        "Customized Pruned FP32",
        "customized_without_EE_pruned_float32.tflite",
        "18Obstacles_labels.txt"
    ),
    CUSTOMIZED_PRUNED_INT8(
        "Customized Pruned INT8",
        "customized_without_EE_pruned_int8.tflite",
        "18Obstacles_labels.txt"
    ),


    // YOLOv8 (Original Model) -----------------------------------------------------------------------
    YOLOV8_FLOAT32(
        "YOLOv8 Float32",
        "yoloV8_float32.tflite",
        "yoloV8_labels.txt"
    );

    /** Default model used across the application when no specific model is selected. */
    companion object {
        val DEFAULT = YOLO8_18_OBSTACLE_FP32BIT
    }
}