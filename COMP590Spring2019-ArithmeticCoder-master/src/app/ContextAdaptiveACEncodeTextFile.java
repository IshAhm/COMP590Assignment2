package app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

import ac.ArithmeticEncoder;
import io.OutputStreamBitSink;
import java.util.Random;
import java.lang.Math;

public class ContextAdaptiveACEncodeTextFile {

	public static void main(String[] args) throws IOException {
		String input_file_name = "data/out.dat";
		String output_file_name = "data/out_myversion.dat";

		int range_bit_width = 40;

		System.out.println("Encoding text file: " + input_file_name);
		System.out.println("Output file: " + output_file_name);
		System.out.println("Range Register Bit Width: " + range_bit_width);

		int num_symbols = (int) new File(input_file_name).length();
				
		Integer[] symbolsDiff = new Integer[511];
		for (int i=-255; i<256; i++) {
			symbolsDiff[i+255] = i;
		}
		
		Integer[] symbolsFirst = new Integer[256];
		for (int i=0; i<256; i++) {
			symbolsFirst[i] = i;
		}
		

		// Create 2 models. Model chosen depends on value of symbol prior to 
		// symbol being encoded. One model is for the first pixel and the other is for the differences
		
		FreqCountIntegerSymbolModel[] models = new FreqCountIntegerSymbolModel[2];
		
		/*
		for (int i=0; i<256; i++) {
			// Create new model with default count of 1 for all symbols
			models[i] = new FreqCountIntegerSymbolModel(symbols);
		}
		*/
		
		//model for first pixel values
		models[0] = new FreqCountIntegerSymbolModel(symbolsFirst);
		
		//create counts of how differences, modeling a normal distribution, 1 on the edges (values -255 and 255)
		//and about 1000 on the center (value 0). Based on the formula that expresses any bell curve as a function of x
		//mean = 0, and std dev = 100
		int[] symDiffCount = new int[511]; 
		for (int i = -255; i<256; i++) {
			double val = (1/(100*Math.sqrt(2*Math.PI)))*Math.exp((-1*Math.pow(i,2))/(20000))*261942 - 39;
			symDiffCount[i+255] = (int) val;
		}
		//why I am adjusting the counts in the middle. Looking at the video, this is essentially a tracking shot of
		//a tractor, which takes up the majority of the frame. Spatial coherence is incredibly important in this case,
		//thus the values in the middle (-20 to 20) must be much more likely than in a normal distribution
		symDiffCount[255] = 100000;
		symDiffCount[254] = 50000;
		symDiffCount[256] = 50000;
		for(int i = 235; i<275; i++) {
			if(i == 255 || i == 254|| i == 256)
				continue;
			symDiffCount[i]=20000;
		}
		models[1] = new FreqCountIntegerSymbolModel(symbolsDiff, symDiffCount);
		
		
		ArithmeticEncoder<Integer> encoder = new ArithmeticEncoder<Integer>(range_bit_width);

		FileOutputStream fos = new FileOutputStream(output_file_name);
		OutputStreamBitSink bit_sink = new OutputStreamBitSink(fos);

		// First 4 bytes are the number of symbols encoded
		bit_sink.write(num_symbols, 32);		

		// Next byte is the width of the range registers
		bit_sink.write(range_bit_width, 8);

		// Now encode the input
		FileInputStream fis = new FileInputStream(input_file_name);
		
		// Use model 0 as initial model.
		FreqCountIntegerSymbolModel model = models[0];

		for (int i=0; i<num_symbols/4; i++) {
			int firstPix = fis.read();
			int[] diff = new int[3];
			for(int k = 0; k<3; k++)
				diff[k] = fis.read() - firstPix;
			encoder.encode(firstPix, models[0], bit_sink);
			
			// Update first pixel model used
			models[0].addToCount(firstPix);
			
			
			//encode the differences
			for(int k=0; k<3; k++) {
				encoder.encode(diff[k], models[1], bit_sink);
			}
		}
		fis.close();

		// Finish off by emitting the middle pattern 
		// and padding to the next word
		
		encoder.emitMiddle(bit_sink);
		bit_sink.padToWord();
		fos.close();
		
		System.out.println("Done");
	}
}
