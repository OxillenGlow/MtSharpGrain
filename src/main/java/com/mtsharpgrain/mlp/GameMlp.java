package com.mtsharpgrain.mlp;

import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.MomentumBackpropagation;
import org.neuroph.util.TransferFunctionType;

import java.io.File;

/**
 * MLP boilerplate for MtSharpGrain.
 *
 * Edit INPUT_SIZE, HIDDEN_SIZE, OUTPUT_SIZE to match your use case,
 * then fill in addTrainingRows() with your actual data.
 * The rest (train / save / load / predict) is ready to use as-is.
 */
public class GameMlp {

    // ── Architecture ────────────────────────────────────────────────────────
    private static final int    INPUT_SIZE  = 3;    // e.g. player x, y, z
    private static final int    HIDDEN_SIZE = 8;    // tune freely
    private static final int    OUTPUT_SIZE = 1;    // e.g. danger score 0-1

    // ── Training hyper-params ────────────────────────────────────────────────
    private static final double LEARNING_RATE   = 0.1;
    private static final double MOMENTUM        = 0.9;
    private static final double MAX_ERROR       = 0.01;  // stop when MSE < this
    private static final int    MAX_ITERATIONS  = 10_000;

    // ── Persistence path ────────────────────────────────────────────────────
    private static final String MODEL_PATH = "src/main/java/com/mtsharpgrain/mlp/model.nnet";

    // ────────────────────────────────────────────────────────────────────────
    private MultiLayerPerceptron mlp;

    // ── Build a fresh network ────────────────────────────────────────────────
    public void build() {
        mlp = new MultiLayerPerceptron(
                TransferFunctionType.SIGMOID,
                INPUT_SIZE, HIDDEN_SIZE, OUTPUT_SIZE
        );

        MomentumBackpropagation rule = (MomentumBackpropagation) mlp.getLearningRule();
        rule.setLearningRate(LEARNING_RATE);
        rule.setMomentum(MOMENTUM);
        rule.setMaxError(MAX_ERROR);
        rule.setMaxIterations(MAX_ITERATIONS);
    }

    // ── Assemble training data ───────────────────────────────────────────────
    private DataSet buildTrainingSet() {
        DataSet ds = new DataSet(INPUT_SIZE, OUTPUT_SIZE);

        // TODO: replace with real rows.
        // Format: addRow(ds, new double[]{input...}, new double[]{expected output...});
        addRow(ds, new double[]{0.0, 0.0, 0.0}, new double[]{0.0});
        addRow(ds, new double[]{1.0, 0.0, 0.0}, new double[]{0.5});
        addRow(ds, new double[]{1.0, 1.0, 1.0}, new double[]{1.0});

        return ds;
    }

    private static void addRow(DataSet ds, double[] inputs, double[] outputs) {
        
        ds.add(inputs, outputs);
    }

    // ── Train — automatically saves the model file when done ────────────────
    public void train() {
        if (mlp == null) build();
        System.out.println("[MLP] Training started…");
        mlp.learn(buildTrainingSet());
        System.out.println("[MLP] Training finished.");
        save();  // weights are written to MODEL_PATH immediately after training
    }

    // ── Save ─────────────────────────────────────────────────────────────────
    public void save() {
        mlp.save(MODEL_PATH);
        System.out.println("[MLP] Model saved → " + MODEL_PATH);
    }

    // ── Load ─────────────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public void load() {
        if (!new File(MODEL_PATH).exists()) {
            System.err.println("[MLP] No saved model found at " + MODEL_PATH + " — train first.");
            return;
        }
        mlp = (MultiLayerPerceptron) NeuralNetwork.createFromFile(MODEL_PATH);
        System.out.println("[MLP] Model loaded ← " + MODEL_PATH);
    }

    // ── Predict ──────────────────────────────────────────────────────────────
    /**
     * Pass in INPUT_SIZE doubles, get OUTPUT_SIZE doubles back.
     * All values should be normalised to [0, 1] to match SIGMOID output.
     */
    public double[] predict(double... inputs) {
        if (mlp == null) {
            System.err.println("[MLP] Network not initialised — call build() or load() first.");
            return new double[OUTPUT_SIZE];
        }
        mlp.setInput(inputs);
        mlp.calculate();
        return mlp.getOutput();
    }

    // ── Quick smoke-test (run standalone) ────────────────────────────────────
    // Flow: train → auto-saves model.nnet → load from file → predict
    public static void main(String[] args) {
        GameMlp net = new GameMlp();
        net.train();  // trains + saves model.nnet automatically

        // Simulate a fresh session: discard in-memory weights and reload from
        // disk to prove the file round-trip actually works.
        GameMlp fresh = new GameMlp();
        fresh.load();

        double[] result = fresh.predict(1.0, 0.5, 0.0);
        System.out.printf("[MLP] predict(1.0, 0.5, 0.0) → %.4f%n", result[0]);
    }
}
