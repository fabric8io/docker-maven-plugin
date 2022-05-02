package io.fabric8.maven.docker.util;

import java.util.*;

import io.fabric8.maven.docker.service.QueryService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author roland
 * @since 16.10.14
 */
@ExtendWith(MockitoExtension.class)
class StartOrderResolverTest {

    @Mock
    private QueryService queryService;
    
    @BeforeEach
    void setup() throws Exception {
        Mockito.doReturn(false)
            .when(queryService)
            .hasContainer(Mockito.anyString());
    }
    
    @Test
    void simple() {
        checkData(new Object[][]{
                { new T[]{new T("1")}, new T[]{new T("1")}},
                { new T[]{new T("1", "2"), new T("2")}, new T[]{new T("2"), new T("1", "2")} },
                { new T[]{new T("1", "2", "3"), new T("2", "3"), new T("3")}, new T[]{new T("3"), new T("2", "3"), new T("1", "2", "3")} },
        });
    }

    @Test
    void circularDep() {
        Object[][] data = {
            { new T[] { new T("1", "2"), new T("2", "1") }, new T[] { new T("1", "2"), new T("2", "1") } }
        };
        Assertions.assertThrows(IllegalStateException.class, () -> checkData(data));
    }

    private void checkData(Object[][] data) {
        for (Object[] aData : data) {
            StartOrderResolver.Resolvable[] input = (StartOrderResolver.Resolvable[]) aData[0];
            StartOrderResolver.Resolvable[] expected = (StartOrderResolver.Resolvable[]) aData[1];
            List<StartOrderResolver.Resolvable> result = StartOrderResolver.resolve(queryService, Arrays.asList(input));
            Assertions.assertArrayEquals(expected, new ArrayList<>(result).toArray());
        }
    }


    // ============================================================================

    private static class T implements StartOrderResolver.Resolvable {

        private String id;
        private List<String> deps;

        private T(String id,String ... dep) {
            this.id = id;
            deps = new ArrayList<>();
            for (String d : dep) {
                deps.add(d);
            }
        }

        @Override
        public String getName() {
            return id;
        }

        @Override
        public String getAlias() {
            return null;
        }

        @Override
        public List<String> getDependencies() {
            return deps;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            T t = (T) o;

            if (id != null ? !id.equals(t.id) : t.id != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "T{" +
                   "id='" + id + '\'' +
                   '}';
        }
    }
}
