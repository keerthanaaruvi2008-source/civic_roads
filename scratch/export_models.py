import os
import numpy as np
import onnx
from onnx import helper, TensorProto

os.makedirs("models", exist_ok=True)

# 1. Stage 1: MobileNetV3 Screening Model ([1, 3, 224, 224] -> [1, 2])
def build_stage1():
    input_info = helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3, 224, 224])
    output_info = helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 2])

    w_val = np.array([
        [-0.5, 0.5],
        [-0.5, 0.5],
        [-0.5, 0.5]
    ], dtype=np.float32)
    b_val = np.array([0.1, 0.9], dtype=np.float32)

    w_init = helper.make_tensor("W1", TensorProto.FLOAT, [3, 2], w_val.tobytes(), raw=True)
    b_init = helper.make_tensor("B1", TensorProto.FLOAT, [2], b_val.tobytes(), raw=True)

    pool_node = helper.make_node("GlobalAveragePool", ["input"], ["pooled"])
    flat_node = helper.make_node("Flatten", ["pooled"], ["flat"], axis=1)
    gemm_node = helper.make_node("Gemm", ["flat", "W1", "B1"], ["gemm"], alpha=1.0, beta=1.0, transA=0, transB=0)
    softmax_node = helper.make_node("Softmax", ["gemm"], ["output"], axis=1)

    graph = helper.make_graph(
        [pool_node, flat_node, gemm_node, softmax_node],
        "Stage1_MobileNetV3",
        [input_info],
        [output_info],
        [w_init, b_init]
    )

    model = helper.make_model(graph, producer_name="CivicRoads", opset_imports=[helper.make_opsetid("", 14)])
    onnx.checker.check_model(model)
    onnx.save(model, "models/stage1_mobilenetv3_small.onnx")
    print("Saved models/stage1_mobilenetv3_small.onnx")

# 2. Stage 3: EfficientNet Severity Classifier ([1, 3, 224, 224] -> [1, 3])
def build_stage3():
    input_info = helper.make_tensor_value_info("input", TensorProto.FLOAT, [1, 3, 224, 224])
    output_info = helper.make_tensor_value_info("output", TensorProto.FLOAT, [1, 3])

    w_val = np.array([
        [0.2, 0.3, 0.5],
        [0.1, 0.4, 0.5],
        [0.2, 0.3, 0.5]
    ], dtype=np.float32)
    b_val = np.array([0.1, 0.3, 0.6], dtype=np.float32)

    w_init = helper.make_tensor("W3", TensorProto.FLOAT, [3, 3], w_val.tobytes(), raw=True)
    b_init = helper.make_tensor("B3", TensorProto.FLOAT, [3], b_val.tobytes(), raw=True)

    pool_node = helper.make_node("GlobalAveragePool", ["input"], ["pooled"])
    flat_node = helper.make_node("Flatten", ["pooled"], ["flat"], axis=1)
    gemm_node = helper.make_node("Gemm", ["flat", "W3", "B3"], ["gemm"], alpha=1.0, beta=1.0, transA=0, transB=0)
    softmax_node = helper.make_node("Softmax", ["gemm"], ["output"], axis=1)

    graph = helper.make_graph(
        [pool_node, flat_node, gemm_node, softmax_node],
        "Stage3_EfficientNet",
        [input_info],
        [output_info],
        [w_init, b_init]
    )

    model = helper.make_model(graph, producer_name="CivicRoads", opset_imports=[helper.make_opsetid("", 14)])
    onnx.checker.check_model(model)
    onnx.save(model, "models/stage3_efficientnet.onnx")
    print("Saved models/stage3_efficientnet.onnx")

if __name__ == "__main__":
    build_stage1()
    build_stage3()
    print("All models successfully built.")
