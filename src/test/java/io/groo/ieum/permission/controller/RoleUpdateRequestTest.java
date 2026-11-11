package io.groo.ieum.permission.controller;

import io.groo.ieum.permission.controller.dto.RoleApiDto;
import io.groo.ieum.permission.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("역할 수정 요청 분기 테스트")
class RoleUpdateRequestTest {
    private final RoleService roleService = mock(RoleService.class);
    private final RoleApiController controller = new RoleApiController(roleService);

    @Test
    @DisplayName("설명을 빈 글로 보내면 서비스까지 간다(예전엔 조용히 무시됐다)")
    void emptyDescReachesService() {
        controller.updateRole("ADMIN", new RoleApiDto.UpdateRoleReq(null, "", null));

        verify(roleService).updateRole("ADMIN", null, "");
    }

    @Test
    @DisplayName("이름만 보내면 이름 수정으로 간다")
    void nameOnlyReachesService() {
        controller.updateRole("ADMIN", new RoleApiDto.UpdateRoleReq("운영자", null, null));

        verify(roleService).updateRole("ADMIN", "운영자", null);
    }

    @Test
    @DisplayName("권한이 있으면 이름 · 설명과 같이 권한 포함 수정으로 간다")
    void withPermissions() {
        controller.updateRole("ADMIN", new RoleApiDto.UpdateRoleReq("운영자", "설명", List.of("USER:READ")));

        verify(roleService).updateRoleWithPermissions("ADMIN", "운영자", "설명", List.of("USER:READ"));
        verify(roleService, never()).updateRole(any(), any(), any());
    }

    @Test
    @DisplayName("아무 칸도 없으면 아무것도 바꾸지 않는다")
    void nothingToChange() {
        controller.updateRole("ADMIN", new RoleApiDto.UpdateRoleReq(null, null, List.of()));

        verifyNoInteractions(roleService);
        verify(roleService, never()).updateRoleWithPermissions(any(), any(), any(), anyList());
    }
}
