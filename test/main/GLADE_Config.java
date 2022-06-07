// Copyright 2015-2016 Stanford University
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main;

import glade.grammar.fuzz.GrammarFuzzer.GrammarMutationSampler;
import glade.grammar.fuzz.GrammarFuzzer.GrammarSampler;
import glade.grammar.fuzz.GrammarFuzzer.CombinedMutationSampler;
import glade.grammar.fuzz.GrammarFuzzer.SampleParameters;
import glade.grammar.GrammarUtils.Grammar;
import glade.grammar.synthesize.GrammarSynthesis;
import glade.util.Log;
import glade.util.OracleUtils.DiscriminativeOracle;
import glade.main.GrammarDataUtils;
import glade.grammar.GrammarUtils.Node;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Stack;

public class GLADE_Config {
	public static final HashMap<String, String[]> option_and_valid_choices = new HashMap<>();
    public static final HashMap<String, Boolean> option_and_boolean = new HashMap<>();
    public static final HashMap<String, File> option_and_directory = new HashMap<>();
    public static final HashMap<String, String> option_and_name = new HashMap<>();
    public static final HashMap<String, Integer> option_and_number = new HashMap<>();
	public static File out_fuzz;
	// public static FileWriter out_fuzz_writer;
	// abstracted to run custom (one-line) commands to the command line 
	public static class TestOracle implements DiscriminativeOracle {
		// returns true if query is a string of matching parentheses
		public boolean query(String query) {
			//create a file with the file extension, put the query inside, and then run with program name
			String stream_output = "";
			try{

				//write the query string to the file
				FileWriter out_fuzz_writer = new FileWriter(out_fuzz, false);
				out_fuzz_writer.write(query);
				out_fuzz_writer.close();

                //make the command
				String command = option_and_name.get("target_program") + " " + out_fuzz.getName();

				//Run and get the output of the stream
				Process process = Runtime.getRuntime().exec(command);
                process.waitFor();
				if(option_and_boolean.get("input_or_error_stream")){
					stream_output = read(process.getInputStream());
				}
				else{
					stream_output = read(process.getErrorStream());
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}

			if(option_and_boolean.get("stream_empty?")){
				return stream_output.isEmpty();
			}
			else{
				return !stream_output.isEmpty();
			}
		}
	}

	public static String read(InputStream input) {
        /**
         * Method to read input from stream and return as readable text
         */
		try {
			StringBuilder result = new StringBuilder();
			BufferedReader br = new BufferedReader(new InputStreamReader(input));
			String line;
			while((line = br.readLine()) != null) {
				result.append(line).append("\n");
			}
			br.close();
			return result.toString();
		} catch (IOException e) {
			throw new RuntimeException("Error reading program output stream!", e);
		}
	}

	
	public static SampleParameters getSampleParameters() {
		return new SampleParameters(new double[]{0.2, 0.2, 0.2, 0.4}, // (multinomial) distribution of repetitions
				0.8,                                          // probability of using recursive production
				0.1,                                          // probability of a uniformly random character (vs. a special character)
				100);                                         // max number of steps before timing out
	}


	public static void main(String[] args) {
		setup_load_defaults(); // method to load defaults; these options can be ignored by the user
        setup_options_and_valid_choices(); //Setup to read binary options with abstraction
        setup_read_config(args); //read from config file
        check_essential_options(); //make sure essential options are read. If not, print error and exit.
		check_out_fuzz(); //make sure the file used for fuzzing exists

		//Settings that need to be done for both
		String logName = option_and_name.get("log_file_name");
		boolean verbose = option_and_boolean.get("log_verbose?");
		Log.init(logName, verbose);
		DiscriminativeOracle oracle = new TestOracle();

        //This is really only used by the fuzzing part, so put this in there
        String output_dir = option_and_directory.get("grammar_output_folder").toString()+ File.separator + option_and_name.get("target_program");

        //TODO: put the two below stuff in their own functions that take in the oracle
        // 
		if(option_and_boolean.get("learn_or_fuzz")){ //learning
            //clear the directory where the grammar will be stored in because that's what the devs did
			GrammarDataUtils.clearGrammarDirectory(option_and_directory.get("grammar_output_folder").toString(), option_and_name.get("target_program"));
			
            //get all files that have the extension designated by the user
            File[] files = get_programs_in_directory(option_and_directory.get("sample_input_folder"), option_and_name.get("file_extension"));
			
            List<Node> roots = new ArrayList<>();

			//Learn from all inputs and save the grammars
			for(int i = 0; i < files.length; i++){
				try{
					File f = files[i];
					String example = Files.readString(f.toPath(), StandardCharsets.UTF_8);
					// String grammar_file_name = output_dir+ File.separator + "example" + i + ".gram";
					Grammar grammar = GrammarSynthesis.getGrammarSingle(example, oracle);
					roots.add(grammar.node);
					// GrammarDataUtils.saveGrammar(grammar_file_name, grammar);
				}catch(Exception e){
					e.printStackTrace();
				}
			}

			//merge and save all the grammars created so far
			Grammar grammar = GrammarSynthesis.getGrammarMultipleFromRoots(roots, oracle);
			GrammarDataUtils.saveAllGrammar(option_and_directory.get("grammar_output_folder").toString(), option_and_name.get("target_program"), grammar);

			//if save_CFG? then ignore for now and say it is work in progress
		}
		else{ //fuzzing
			//grammar location = output_folder + "/" + program_name + "/" + all.gram
            //Check if the grammar was actually learned first
            File all_gram_file = new File(output_dir + File.separator + "all.gram");

            //if it doesn't print an error and exit
            if(!all_gram_file.exists()){
                System.out.printf("The grammar for program %s was not learned!\n", option_and_name.get("target_program"));
                System.out.printf("Cannot find file %s!\n", all_gram_file.toString());
                System.exit(0);
            } 
            Grammar grammar = GrammarDataUtils.loadGrammar(output_dir + File.separator + "all.gram");
            int maxLen = option_and_number.get("sample_max_len"); // max length of a sample
            int numMut = option_and_number.get("num_mutations"); // number of mutations to seed input
            SampleParameters sampleParams = getSampleParameters(); // sampling parameters
            int seed = option_and_number.get("random_seed"); // seed for random number generator

            Iterable<String> samples = null; //Done because Java compiler will complain if I don't
            String chosen_fuzzer_name = option_and_name.get("fuzz_type");
            switch(chosen_fuzzer_name){
                case "grammar":
                    samples = new GrammarSampler(grammar, sampleParams, new Random(seed));
                    break;
                case "mutation":
                    samples = new GrammarMutationSampler(grammar, sampleParams, maxLen, numMut, new Random(seed));
                    break;
                case "combined":
                    Iterable<String> mutationFuzzer = new GrammarMutationSampler(grammar, sampleParams, maxLen, numMut, new Random(seed));
                    samples = new CombinedMutationSampler(mutationFuzzer, numMut, new Random(seed));
                    break;
                default:
                    System.out.printf("Invalid choice of fuzzer: %s", chosen_fuzzer_name);
                    break;
            }

            int numSamples = option_and_number.get("fuzz_output_num");
            int pass = 0;
            int count = 0;
            for(String sample : samples) {
            	Log.info("SAMPLE: " + sample);
            	if(oracle.query(sample)) {
            		Log.info("PASS");
            		pass++;
            	} else {
            		Log.info("FAIL");
            	}
            	Log.info("");
            	count++;
            	if(count >= numSamples) {
            		break;
            	}
            }
            Log.info("PASS RATE: " + (float)pass/numSamples);
		}
	}

	private static void check_out_fuzz(){
		out_fuzz = new File("out_fuzz" + option_and_name.get("file_extension"));
		if(!out_fuzz.exists()){
			try{
				out_fuzz.createNewFile();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		out_fuzz.deleteOnExit();
	}

	private static void setup_read_config(String[] args){
        String config_file_name;
        if(args.length == 0){
            config_file_name = "GLADE_config.txt";
        }
        else{
            config_file_name = args[0];
        }
        System.out.println("Config filename is: " + config_file_name);
        File config_file = new File(config_file_name);
        if(!config_file.exists()){
            System.out.printf("Error: Config File %s does not exist!\n", config_file);
            System.exit(0);
        }
        try {
            Scanner config_scanner = new Scanner(config_file);
            while(config_scanner.hasNextLine()){
                String next_line = config_scanner.nextLine();

                next_line = next_line.split("#")[0]; //ignore all comments
                if(next_line.isBlank()){ //if now blank, then ignore
                    continue;
                }

                //read the string and configure it
                String[] optionAndChoice = get_option_and_choice(next_line);
                read_option_and_choice(optionAndChoice[0], optionAndChoice[1]);
            }
            config_scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void setup_options_and_valid_choices(){
        String[] learn_or_fuzz_arr = new String[]{"learn", "fuzz"};
        String[] input_or_error_arr = new String[]{"input", "error"};
        String[] yes_or_no_arr = new String[]{"yes", "no"};
        option_and_valid_choices.put("learn_or_fuzz", learn_or_fuzz_arr);
        option_and_valid_choices.put("input_or_error_stream", input_or_error_arr);
        option_and_valid_choices.put("stream_empty?", yes_or_no_arr);
        option_and_valid_choices.put("save_CFG?", yes_or_no_arr);
        option_and_valid_choices.put("log_verbose?", yes_or_no_arr);
    }

    private static void setup_load_defaults(){
        Random rand = new Random();
        option_and_number.put("fuzz_output_num", 10);
        option_and_name.put("log_file_name", "log.txt");
        option_and_boolean.put("log_verbose?", true);
        option_and_number.put("random_seed", rand.nextInt());
        option_and_number.put("sample_max_len", 1000);
        option_and_number.put("num_mutations", 20);
        option_and_name.put("fuzz_type", "mutation");
    }

    private static String[] get_option_and_choice(String line){
        /**
         * Method to read from config file line and get option + choice string array
         */
        String current_option_and_choice = line.trim();
        // current_option_and_choice = current_option_and_choice.replaceAll("\\s","");
        String[] optionAndChoice = current_option_and_choice.split("=");
        optionAndChoice[0] = optionAndChoice[0].trim();
        optionAndChoice[1] = optionAndChoice[1].trim();
        if(optionAndChoice.length < 2){
            System.out.printf("Error: Invalid option line:\n\t%s\n", line);
            System.exit(0);
        }
        return optionAndChoice;
    }

    private static boolean binary_option_to_boolean(String option, String choice, String[] valid_choices){
        /**
         * Return true if choice = valid_choice[0]
         * false if choice = valid_choice[1]
         * Print error and exit otherwise
         */
        if(valid_choices.length < 2){
            System.out.println("This is the fault of the programmer.");
            System.out.println("Function boolean binary_option_to_boolean was called with the following params.");
            System.out.printf("option: %s\nchoice: %s\nvalid_choices: %s\n", option, choice, Arrays.toString(valid_choices));
            System.out.println("Error: valid_choices contains less than 2 valid choices");
            System.exit(0);
        }
        if(choice.equals(valid_choices[0])){
            return true;
        }
        else if(choice.equals(valid_choices[1])){
            return false;
        }
        else{
            System.out.printf("Invalid value for option %s: %s\n", option, choice);
            System.out.printf("Valid values are:\n\t%s\n\t%s\n", valid_choices[0], valid_choices[1]);
            System.exit(0);
        }
        return false; //This should not even be reached but the Java compiler will complain if I don't put this here
    }

    public static File path_is_directory(String directoryName){
        /**
         * Check if the given directory name (as a string):
         * 1. Exists
         * 2. Is actually a directory
         */
        File f = new File(directoryName);
        if (!f.exists()){
            System.out.printf("Error: directory %s does not exist\n", directoryName);
            System.exit(0);
        }
        if(!f.isDirectory()){
            System.out.printf("Error: input path %s is not a directory\n", directoryName);
            System.exit(0);
        }
        return f;
    }

	public static File[] get_programs_in_directory(File dir, String ext){
        //filter the files in the input directory for learning
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File f, String name) {
                return name.endsWith(ext);
            }
        };
        File[] files = dir.listFiles(filter);
        if(files.length == 0){
            System.out.printf("Error: Could not find any files with extension %s in directory %s\n", ext, dir.toString());
            System.exit(0);
        }
        return files;
    }

    //Create method the handle each line and return the necessary information (make public strings?)
    private static void read_option_and_choice(String option, String choice){
        switch(option){
            //First, check if any of the binary-choice options
            case "learn_or_fuzz":
            case "input_or_error_stream":
            case "stream_empty?":
            case "log_verbose?":
            case "save_CFG?":
                option_and_boolean.put(option, binary_option_to_boolean(option, choice, option_and_valid_choices.get(option)));
                break;
            //Then check if any of the directory options
            case "sample_input_folder":
            case "grammar_output_folder":
            case "CFG_output_folder":
                option_and_directory.put(option, path_is_directory(choice));
                break;
            //Then check if program name or file extension string
            case "fuzz_type":
            case "log_file_name":
            case "target_program":
                option_and_name.put(option, choice);
                break;
            case "file_extension":
                if(!choice.startsWith(".")){
                    System.out.printf("Error: Invalid value for choice %s: %s\n", option, choice);
                    System.out.println("File extension does not begin with a period.");
                    System.exit(0);
                }
                option_and_name.put(option, choice);
                break;
            //Then check if number-value option
            case "random_seed":
            case "sample_max_len":
            case "num_mutations":
            case "fuzz_output_num":
                try{
                    option_and_number.put(option, Integer.parseInt(choice));
                }catch(Exception e){
                    System.out.printf("Invalid value for option %s: %s\n", option, choice);
                    System.out.printf("%s is not an integer\n", choice);
                    // e.printStackTrace();
                }
                break;
            default:
                System.out.printf("Warning: Option %s is not supported.\n", option);
                System.out.printf("Did you mispell something?\n");
                //Warn the programmer (me) which options have not been implemented yet
        }
    }

    //method to check if all essential options (ones without defaults) are here
    //if .get in the hashmaps return a null, then error and exit
    //also prints out all options that need a value
    private static void check_essential_options(){
        String[] essentialOptions_bool = new String[]{"learn_or_fuzz", "input_or_error_stream", "stream_empty?"};
        String[] essentialOptions_dir = new String[]{"sample_input_folder", "grammar_output_folder"};
        String[] essentialOptions_name = new String[]{"target_program","file_extension"};
        int numErrors = 0;
        for(String s: essentialOptions_bool){
            if(option_and_boolean.get(s) == null){
                System.out.printf("Error: Option %s does not have a value in the config file.\n", s);
                numErrors++;
            }
        }
        for(String s: essentialOptions_dir){
            if(option_and_directory.get(s) == null){
                System.out.printf("Error: Option %s does not have a value in the config file.\n", s);
                numErrors++;
            }
        }
        for(String s: essentialOptions_name){
            if(option_and_name.get(s) == null){
                System.out.printf("Error: Option %s does not have a value in the config file.\n", s);
                numErrors++;
            }
        }
        if(option_and_boolean.get("save_CFG?")){ //this is only essential if is true (false by default)
            if(option_and_directory.get("CFG_output_folder") == null){
                System.out.printf("Error: Option %s does not have a value in the config file.\n", "CFG_output_folder");
                numErrors++;
            }
        }
        if(numErrors > 0){
            System.out.printf("Please fix the %d error(s) above in the config file.\n", numErrors);
            System.exit(0);
        }
        
    }
	
}