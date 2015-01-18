
# Integration Testing

This document currently only holds some ideas of how and what to integration test this plugin

### Registry Handling

* Specify registry via environment variable, global configuration, as image configuration or with name
* Test that `autoPull` works for `docker:start`
* Check that push works with the registry. Also check, that no temporary docker images names (which needs to
  be created before pushing) are left over.