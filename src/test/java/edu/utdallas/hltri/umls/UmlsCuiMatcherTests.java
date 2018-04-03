package edu.utdallas.hltri.umls;

import com.google.common.collect.Iterables;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Created by travis on 7/21/16.
 */
public class UmlsCuiMatcherTests {
  final static UmlsService umls = new UmlsService("2016AA");

  final static UmlsCuiMatcher matcher = UmlsCuiMatcher.forCui("C0027051");

  final String definition = "Myocardial Infarction (MI) is the necrosis of the myocardium caused by an obstruction of the" +
                            "blood supply to the heart and often associated with chest pain, shortness of breath, " +
                            "palpitations, and anxiety as well as characteristic EKG findings and elevation of serum" +
                            "markers including creatine kinase-MB fraction and troponin";

  @Test
  public void testNumAtoms() {
    assertEquals(matcher.getNumAtoms(), 393);
  }

  @Test
  public void testNumCuis() {
    assertEquals(matcher.getNumCuis(), 105);
  }

  @Test
  public void testHasMatchWithin() {
    assertTrue(matcher.hasMatchWithin(definition));
  }

  @Test
  public void testGetAtomsWithin() {
    assertTrue(Iterables.elementsEqual(matcher.getAtomsWithin(definition), Arrays.asList("Myocardial Infarction", "Myocardial Infarction MI", "MI")));
  }

  @Test
  public void testGetCuisWithin() {
   assertEquals(Iterables.getFirst(matcher.getCuisWithin(definition), null), "C0027051");
  }

  @Test
  public void testStrokeMatches() {
    final UmlsCuiMatcher strokeMatcher = UmlsCuiMatcher.forCui("C0038454");
    final String matchText = "TSICU to order HR/BP meds this evening on reccomendations from stroke team.";
    assertTrue(strokeMatcher.hasMatchWithin(matchText));
  }
}
