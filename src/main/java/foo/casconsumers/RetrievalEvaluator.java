package foo.casconsumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import foo.typesystems.Document;
import foo.typesystems.Token;
import foo.utils.Utils;


public class RetrievalEvaluator extends CasConsumer_ImplBase {

	/** query id number **/
	public ArrayList<Integer> qIdList;

	/** query and text relevant values **/
	public ArrayList<Integer> relList;
	
	/** query stem lists **/
	public ArrayList<HashMap<String, Integer>> stemMapLists;

	/** doc sentence list **/
	public ArrayList<String> sentList;
	
	/** mmr value **/
	public double mrr;
  

		
	public void initialize() throws ResourceInitializationException {

		qIdList = new ArrayList<Integer>();

		relList = new ArrayList<Integer>();
		
		stemMapLists = new ArrayList<HashMap<String,Integer>>();
		
		sentList = new ArrayList<String>();
		
		mrr = 0;

	}

	/**
	 * TODO :: 1. construct the global word dictionary 2. keep the word
	 * frequency for each sentence
	 */
	@Override
	public void processCas(CAS aCas) throws ResourceProcessException {

		JCas jcas;
		try {
			jcas =aCas.getJCas();
		} catch (CASException e) {
			throw new ResourceProcessException(e);
		}
	
		FSIterator it = jcas.getAnnotationIndex(Document.type).iterator();
	
		if (it.hasNext()) {
			Document doc = (Document) it.next();
			
	    //Make sure that your previous annotators have populated this in CAS
      FSList fsTokenList = doc.getTokenList();     
      ArrayList<Token>tokenList=Utils.fromFSListToCollection(fsTokenList, Token.class);
      
      HashMap<String, Integer> docHashMap = new HashMap<String, Integer>();
      for(Token token : tokenList){
        docHashMap.put(token.getText(), token.getFrequency());        
      }     

      //Do something useful here
      stemMapLists.add(docHashMap);			
      qIdList.add(doc.getQueryID());
			relList.add(doc.getRelevanceValue());
			sentList.add(doc.getText());		

		}

	}

	/**
	 * TODO 1. Compute Cosine Similarity and rank the retrieved sentences 2.
	 * Compute the MRR metric
	 */
	@Override
	public void collectionProcessComplete(ProcessTrace arg0)
			throws ResourceProcessException, IOException {

		super.collectionProcessComplete(arg0);

		
		int qNum = 0;
		//compute the cosine similarity measure, the rank of retrieved sentences and the metric:: mean reciprocal rank
		HashMap<String, Integer> queryHashMap = new HashMap<String, Integer>();
		HashMap<String, Integer> docHashMap = new HashMap<String, Integer>();
		boolean ifFirst = true;
		
		HashMap<Integer, Double> resultMap = new HashMap<Integer,Double>();
		int count = qIdList.size();
		
		for(int i = 0; i < count; i++){
		  if(relList.get(i) == 99){
		    qNum++;
		    queryHashMap = stemMapLists.get(i);
		    if(ifFirst){
		      //do nothing
		      ifFirst = false;
		    }
		    else{
		      //compute last query's answer with highest cosine score
		      rankDocs(resultMap);
          resultMap = new HashMap<Integer,Double>();
		    }
		  }
		  else{
		    docHashMap = stemMapLists.get(i);
		    double cos = computeCosineSimilarity(queryHashMap, docHashMap);
		    resultMap.put(i, cos);
		  }
		}
		rankDocs(resultMap);

		mrr = (double)mrr / (double)qNum;
		System.out.println(" (MRR) Mean Reciprocal Rank ::" + mrr);
	}

	/**
	 * 
	 * @return cosine_similarity
	 */
	private double computeCosineSimilarity(Map<String, Integer> queryVector,
			Map<String, Integer> docVector) {
		double cosine_similarity=0.0;
		double qNor = 0,dNor = 0,innerProduct = 0;
		//compute cosine similarity between two sentences
		Iterator qIter = queryVector.entrySet().iterator(); 
		
		while (qIter.hasNext()) { 
		    Map.Entry entry = (Map.Entry) qIter.next(); 
		    String stem = (String)entry.getKey(); 
		    int freq = (Integer)entry.getValue();
		    
		    if(docVector.containsKey(stem)){
		      innerProduct += docVector.get(stem) * freq;
		    }
		    qNor += freq * freq;
		} 
		
		qNor = Math.sqrt(qNor);
		
		
    Iterator dIter = docVector.entrySet().iterator(); 
    
    while (dIter.hasNext()) { 
        Map.Entry entry = (Map.Entry) dIter.next(); 
        int freq = (Integer)entry.getValue();
        
        dNor += freq * freq;
        
    }
    
    dNor = Math.sqrt(dNor);
    
    cosine_similarity = innerProduct / (qNor * dNor);
    
		return cosine_similarity;
	}

	 /**
   * 
   * @return dice_coefficient
   */
	private double computeDiceCoefficient(Map<String, Integer> queryVector,
	         Map<String, Integer> docVector) {
	  
	  double diceCoeff;
	  int qNor,dNor,intersect = 0;
	  //compute dice coefficient between two sentences
	  qNor = queryVector.size();
	  dNor = docVector.size();
	  
	  Iterator qIter = queryVector.entrySet().iterator();	  
	  while (qIter.hasNext()) {
	    Map.Entry entry = (Map.Entry) qIter.next();
	    String stem = (String)entry.getKey();
	    if(docVector.containsKey(stem)){
	      intersect++;
	    }	        
	  } 
	  
	  diceCoeff = 2 * (double)intersect / (double)(qNor + dNor);
	  return diceCoeff;
	}
	
  /**
   * 
   * @return jaccard_coefficient
   */
  private double computeJaccardCoefficient(Map<String, Integer> queryVector,
           Map<String, Integer> docVector) {
    
    double jaccardCoeff;
    int qNor,dNor,intersect = 0;
    //compute jaccard coefficient between two sentences
    qNor = queryVector.size();
    dNor = docVector.size();
    
    Iterator qIter = queryVector.entrySet().iterator();   
    while (qIter.hasNext()) {
      Map.Entry entry = (Map.Entry) qIter.next();
      String stem = (String)entry.getKey();
      if(docVector.containsKey(stem)){
        intersect++;
      }         
    } 
    
    jaccardCoeff = (double)intersect / (double)(qNor + dNor - intersect);
    return jaccardCoeff;
  }
	
	/**
	 * rank the retrieved documents
	 */
	private void rankDocs(HashMap<Integer, Double> resultMap) {
    ArrayList<Map.Entry<Integer,Double>> l = new ArrayList<Map.Entry<Integer,Double>>(resultMap.entrySet());  
    
    Collections.sort(l, new Comparator<Map.Entry<Integer,Double>>() {  
      public int compare(Map.Entry<Integer,Double> o1, Map.Entry<Integer,Double> o2) {
        double difference = o2.getValue() - o1.getValue();
        if(difference != 0){
          return difference > 0 ? 1 : -1;
        }
        else{
          return 0;
        }
      }  
    });
        
    int resultSize = l.size();
    for (int j = 0; j < resultSize; j++) {
      int index = l.get(j).getKey();
      
      
      if(relList.get(index) == 1){
        mrr =  mrr + (1/(double)(j + 1));
        System.out.println("Score: " + l.get(j).getValue() + "  rank=" + (j+1) + "  rel=1 qid=" + qIdList.get(index) + " " + sentList.get(index));
        break;
      }
    }
    
	}

}
