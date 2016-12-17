package io.fabric8.maven.docker.model;
/*
 *
 * Copyright 2014 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;

/**
 * Interface representing a container
 *
 * @author Tom Burton
 * @since 12/15/16
 */
public interface Volume {

   String getName();
   
   String getDriver();
   
   String getMountpoint();
   
   Map<String, String> getStatus();
   
   Map<String, String> getLabels();
   
   String getScope();
    
}
