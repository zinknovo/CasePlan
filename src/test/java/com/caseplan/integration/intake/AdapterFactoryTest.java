package com.caseplan.integration.intake;

import com.caseplan.integration.intake.adapter.BaseIntakeAdapter;
import com.caseplan.integration.intake.adapter.JsonAAdapter;
import com.caseplan.integration.intake.adapter.JsonBAdapter;
import com.caseplan.integration.intake.adapter.XmlAdapter;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.*;

public class AdapterFactoryTest {

    private AdapterFactory factory;

    @Before
    public void setup() {
        factory = new AdapterFactory(Arrays.asList(
                new JsonAAdapter(),
                new JsonBAdapter(),
                new XmlAdapter()
        ));
    }

    @Test
    public void getAdapter_jsonA_returnsJsonAAdapter() {
        Optional<BaseIntakeAdapter> adapter = factory.getAdapter("jsonA");
        assertTrue(adapter.isPresent());
        assertTrue(adapter.get() instanceof JsonAAdapter);
    }

    @Test
    public void getAdapter_jsonB_returnsJsonBAdapter() {
        Optional<BaseIntakeAdapter> adapter = factory.getAdapter("jsonB");
        assertTrue(adapter.isPresent());
        assertTrue(adapter.get() instanceof JsonBAdapter);
    }

    @Test
    public void getAdapter_xml_returnsXmlAdapter() {
        Optional<BaseIntakeAdapter> adapter = factory.getAdapter("xml");
        assertTrue(adapter.isPresent());
        assertTrue(adapter.get() instanceof XmlAdapter);
    }

    @Test
    public void getAdapter_caseInsensitive() {
        assertTrue(factory.getAdapter("JSONA").isPresent());
        assertTrue(factory.getAdapter("JsonA").isPresent());
        assertTrue(factory.getAdapter("XML").isPresent());
    }

    @Test
    public void getAdapter_withWhitespace_trimmed() {
        assertTrue(factory.getAdapter("  jsonA  ").isPresent());
    }

    @Test
    public void getAdapter_unknown_returnsEmpty() {
        assertFalse(factory.getAdapter("unknown").isPresent());
    }

    @Test
    public void getAdapter_null_returnsEmpty() {
        assertFalse(factory.getAdapter(null).isPresent());
    }

    @Test
    public void getAdapter_empty_returnsEmpty() {
        assertFalse(factory.getAdapter("").isPresent());
    }
}
