package app;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

import ac.ArithmeticDecoder;
import io.InputStreamBitSource;
import io.InsufficientBitsLeftException;

public class ContextAdaptiveACDecodeTextFile {

	public static void main(String[] args) throws InsufficientBitsLeftException, IOException {
		String input_file_name = "data/out_myversion.dat";
		String output_file_name = "data/my_version_uncompressed.dat";

		FileInputStream fis = new FileInputStream(input_file_name);

		InputStreamBitSource bit_source = new InputStreamBitSource(fis);

		Integer[] symbolsDiff = new Integer[511];
		for (int i=-255; i<256; i++) {
			symbolsDiff[i+255] = i;
		}
		
		Integer[] symbolsFirst = new Integer[256];
		for (int i=0; i<256; i++) {
			symbolsFirst[i] = i;
		}

		// Create 2 models. Model chosen depends on if it's the first pixel 
		// symbol being encoded or the differences.
		
		FreqCountIntegerSymbolModel[] models = new FreqCountIntegerSymbolModel[2];
		
		//model for first pixel values, with default count 1
		models[0] = new FreqCountIntegerSymbolModel(symbolsFirst);
		
		//for some reason the person who encoded did some crazy things. No questions asked, just following instructions.
		int[] symDiffCount = new int[511]; 
		for (int i = -255; i<256; i++) {
			double val = (1/(100*Math.sqrt(2*Math.PI)))*Math.exp((-1*Math.pow(i,2))/(20000))*261942 - 39;
			symDiffCount[i+255] = (int) val;
		}
		symDiffCount[255] = 100000;
		symDiffCount[254] = 50000;
		symDiffCount[256] = 50000;
		for(int i = 235; i<275; i++) {
			if(i == 255 || i == 254|| i == 256)
				continue;
			symDiffCount[i]=20000;
		}
		models[1] = new FreqCountIntegerSymbolModel(symbolsDiff, symDiffCount);
		
		// Read in number of symbols encoded

		int num_symbols = bit_source.next(32);

		// Read in range bit width and setup the decoder

		int range_bit_width = bit_source.next(8);
		ArithmeticDecoder<Integer> decoder = new ArithmeticDecoder<Integer>(range_bit_width);

		// Decode and produce output.
		
		System.out.println("Uncompressing file: " + input_file_name);
		System.out.println("Output file: " + output_file_name);
		System.out.println("Range Register Bit Width: " + range_bit_width);
		System.out.println("Number of encoded symbols: " + num_symbols);
		
		FileOutputStream fos = new FileOutputStream(output_file_name);

		//FreqCountIntegerSymbolModel model = models[0];

		for (int i=0; i<num_symbols/4; i++) {
			int sym = decoder.decode(models[0], bit_source);
			fos.write(sym);
			
			// Update model used (first pixel value)
			models[0].addToCount(sym);
			
			//decode the difference and add the first pixel value to it
			for(int i1 =0; i1<3; i1++) {
				int symDiff = decoder.decode(models[1], bit_source);
				int ogValue = symDiff + sym;
				fos.write(ogValue);
			}
			
			
		}

		System.out.println("Done.");
		fos.flush();
		fos.close();
		fis.close();
	}
}
