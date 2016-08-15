package org.hl7.fhir.dstu3.validation;
/*
Copyright (c) 2011+, HL7, Inc
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, 
are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this 
   list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, 
   this list of conditions and the following disclaimer in the documentation 
   and/or other materials provided with the distribution.
 * Neither the name of HL7 nor the names of its contributors may be used to 
   endorse or promote products derived from this software without specific 
   prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT 
NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

*/

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.hl7.fhir.dstu3.formats.XmlParser;
import org.hl7.fhir.dstu3.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.dstu3.utils.Transformer;
import org.hl7.fhir.utilities.Utilities;

/**
 * A executable class that will validate one or more FHIR resources against 
 * the specification
 * 
 * todo: schema validation (w3c xml, json schema, shex?)
 * @author Grahame
 *
 */
public class Validator {

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.out.println("FHIR Validation tool. ");
      System.out.println("");
      System.out.println("The FHIR validation tool validates a FHIR resource or bundle.");
      System.out.println("The validation tool compares a resource against the base definitions and any");
      System.out.println("profiles declared in the resource (Resource.meta.profile) or specified on the ");
      System.out.println("command line");
      System.out.println("");
      System.out.println("The following resource formats are supported: XML, JSON, Turtle");
      System.out.println("");
      System.out.println("In addition, (if requested) schema is also checked (W3C XML Schema | JSON schema | ShEx)");
      System.out.println("");
      System.out.println("Usage: org.hl7.fhir.validator.jar [source] (parameters)");
      System.out.println("");
      System.out.println("[source] is a file name or url of the resource or bundle feed to validate");
      System.out.println("");
      System.out.println("The following parameters are supported:");
      System.out.println("-defn [file|url]: where to find the FHIR specification definitions.xml.zip or definitions.json.zip");
      System.out.println("      default value is http://hl7.org/fhir. This parameter can only appear once");
      System.out.println("-ig [file|url]: an IG or profile definition to load. Can be the URL of an implementation guide,");
      System.out.println("     or a direct reference to a definitions.xml.zip or definitions.json.zip for a built");
      System.out.println("     implementation guide, or a folder that contains conformance resource, or a direct");
      System.out.println("     reference to a conformance resource");
      System.out.println("     no default value. This parameter can only appear any number of times");
      System.out.println("-tx [url]: the [base] url of a FHIR terminology service");
      System.out.println("     Default value is http://fhir3.healthintersections.com.au/open");
      System.out.println("     To run without terminology value, specific n/a as the URL");
      System.out.println("*-profile [url]: a canonical URL to validate against (same as if it was specified in Resource.meta.profile)");
      System.out.println("     no default value. This parameter can only appear any number of times");
      System.out.println("-questionnaire: validate questionnaire responses against their questionnaire");
      System.out.println("-output [file]: a filename for the results (OperationOutcome)");
      System.out.println("     Default: results are sent to the std out.");
      System.out.println("-native: use schema for validation as well. XML: w3c schema+schematron; JSON: json.schema; RDF: SHEX");
      System.out.println("     Default: false. Note that these are only performed for Implementation Guides if the IG publishes the appropriate schema");
      System.out.println("");
      System.out.println("Parameters can appear in any order");
      System.out.println("");
      System.out.println("Alternatively, you can use the validator to execute a transformation as described by a structure map.");
      System.out.println("To do this, you must provide some additional parameters:");
      System.out.println("");
      System.out.println(" -transform -folder [folder] -map [map-file]");
      System.out.println("");
      System.out.println("* [map] the URI of the map that the transform starts with");
      System.out.println("");
      System.out.println("-transform requires the parameters -defn, -txserver, -folder (at least one with the map files), and -output");
    } else {
      if (args[0].equals("-profile-tests")) {
        String pack = null;
        String registry = null;
        for (int i = 0; i < args.length - 1; i++) {
          if (args[i].equals("-profile-tests"))
            registry = args[i+1];
          if (args[i].equals("-defn"))
            pack = args[i+1];
          	
        }
        ProfileValidatorTests tests = new ProfileValidatorTests(new File(pack), new File(registry));
        tests.execute();
      } else if (hasTransformParam(args)) {
        Transformer exe = new Transformer();
        exe.setSource(args[0]);
        for (int i = 1; i < args.length; i++) {
          if (args[i].equals("-defn"))
            exe.setDefinitions(args[i+1]);
          if (args[i].equals("-output"))
            exe.setOutput(args[i+1]);
          if (args[i].equals("-txserver"))
            exe.setTxServer(args[i+1]);
          if (args[i].equals("-folder"))
            exe.addFolder(args[i+1]);
          if (args[i].equals("-map"))
            exe.setMapUri(args[i+1]);
        }
        if (exe.process()) 
          System.out.println(" ...success");
        else
          System.out.println(" ...failure: "+exe.getMessage());
      } else { 
        String definitions = "http://hl7-fhir.github.io/";
        List<String> igs = new ArrayList<String>();
        String txServer = "http://fhir3.healthintersections.com.au/open";
        boolean questionnaire = false;
        boolean doNative = false;
        List<String> profiles = new ArrayList<String>();
        String output = null;

        // load the parameters - so order doesn't matter
        for (int i = 1; i < args.length; i++) {
          if (args[i].equals("-defn"))
            definitions = args[i+1];
          if (args[i].equals("-output"))
            output = args[i+1];
          if (args[i].equals("-profile"))
            profiles.add(args[i+1]);
          if (args[i].equals("-questionnaire"))
            questionnaire = true;
          if (args[i].equals("-native"))
            doNative = true;
          if (args[i].equals("-tx"))
            txServer = "n/a".equals(args[i+1]) ? null : args[i+1];
          if (args[i].equals("-ig"))
            igs.add(args[i+1]);
        }
        
        ValidationEngine validator = new ValidationEngine();
        validator.loadDefinitions(definitions);
          validator.connectToTSServer(txServer);
        for (String src : igs)
          validator.loadIg(src);
        validator.setQuestionnaire(questionnaire);
        validator.setNative(doNative);
        validator.loadResource(args[0]);
        validator.setProfiles(profiles);
        validator.validate();
        if (output == null) {
          System.out.println("Validating "+args[0]+": "+Integer.toString(validator.getMessages().size())+" messages");
          for (ValidationMessage v : validator.getMessages()) {
            System.out.println(v.summary());
          }
          int count = 0;
          for (ValidationMessage t : validator.getMessages()) {
          	if (t.getLevel() == IssueSeverity.ERROR || t.getLevel() == IssueSeverity.FATAL)
          		count++;
          }
          if (count == 0)
            System.out.println(" ...success");
          else
            System.out.println(" ...failure");
        } else {
          FileOutputStream s = new FileOutputStream(output);
          new XmlParser().compose(s, validator.getOutcome(), true);
          s.close();
        }
      }
    }
  }


	private static boolean hasTransformParam(String[] args) {
		for (String s : args) {
			if (s.equals("-transform"))
				return true;
		}
		return false;
	}

}
