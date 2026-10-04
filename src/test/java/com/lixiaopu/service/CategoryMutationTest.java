package com.lixiaopu.service;

import com.lixiaopu.aop.annotation.UpdateCategoryTreeRedisCacheAnnotation;
import com.lixiaopu.aop.aspect.UpdateCategoryTreeRedisCacheAspect;
import com.lixiaopu.common.result.Result;
import com.lixiaopu.common.util.CaffeineUtils;
import com.lixiaopu.infrastructure.redis.connect.RedisConnector;
import com.lixiaopu.infrastructure.redis.generator.RedisKeyGenerator;
import com.lixiaopu.pojo.dto.CategoryDTO;
import com.lixiaopu.pojo.entity.Category;
import com.lixiaopu.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryMutationTest {
    @Test
    void addKeepsDefaultsAndClearsLocalCacheOnlyAfterSuccessfulSave() {
        SavingService service = new SavingService();
        CaffeineUtils cache = mock(CaffeineUtils.class);
        ReflectionTestUtils.setField(service, "caffeineUtils", cache);
        CategoryDTO dto = new CategoryDTO();
        dto.setName(" 水果 ");

        Result result = service.addCategory(dto);

        assertTrue(result.getSuccess());
        assertEquals("水果", service.saved.getName());
        assertEquals(0L, service.saved.getParentId());
        assertEquals(0, service.saved.getSort());
        assertEquals(1, service.saved.getStatus());
        assertEquals("/static/images/default-category.png", service.saved.getIconUrl());
        verify(cache).invalidateCategoryTree();
    }

    @Test
    void invalidInputAndFailedSaveDoNotClearCache() {
        SavingService service = new SavingService();
        CaffeineUtils cache = mock(CaffeineUtils.class);
        ReflectionTestUtils.setField(service, "caffeineUtils", cache);
        assertEquals(400, service.addCategory(null).getCode());
        CategoryDTO dto = new CategoryDTO();
        dto.setName("水果");
        for (String invalid : List.of("-1", "01", "abc", "9223372036854775808")) {
            dto.setParentId(invalid);
            assertFalse(service.addCategory(dto).getSuccess());
        }
        assertNull(service.saved);
        dto.setParentId(null);
        service.saveSuccess = false;
        assertFalse(service.addCategory(dto).getSuccess());
        verifyNoInteractions(cache);
    }

    @Test
    void emptyTreeDeletesOldRedisHashWithoutWritingEmptyMap() {
        CategoryServiceImpl service = new CategoryServiceImpl();
        CaffeineUtils cache = mock(CaffeineUtils.class);
        RedisConnector redis = mock(RedisConnector.class);
        ReflectionTestUtils.setField(service, "caffeineUtils", cache);
        ReflectionTestUtils.setField(service, "redisConnector", redis);
        when(cache.getCategoryTree()).thenReturn(List.of());
        service.updateCategoryTreeRedisCache();
        verify(redis).delete(RedisKeyGenerator.categoryTreeKey());
        verifyNoMoreInteractions(redis);
    }

    @Test
    void actualAopProxyRefreshesOnlyAfterBusinessSuccess() {
        CategoryService categoryService = mock(CategoryService.class);
        UpdateCategoryTreeRedisCacheAspect aspect = new UpdateCategoryTreeRedisCacheAspect();
        ReflectionTestUtils.setField(aspect, "categoryService", categoryService);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AnnotatedBusiness());
        factory.addAspect(aspect);
        AnnotatedBusiness proxy = factory.getProxy();
        Result success = Result.success("已保存");

        doAnswer(invocation -> {
            assertTrue(AnnotatedBusiness.returned);
            return null;
        }).when(categoryService).updateCategoryTreeRedisCache();
        assertSame(success, proxy.execute(success, false));
        verify(categoryService).updateCategoryTreeRedisCache();
        clearInvocations(categoryService);

        proxy.execute(Result.error(400, "参数不合法"), false);
        proxy.execute(null, false);
        assertThrows(IllegalStateException.class, () -> proxy.execute(success, true));
        verifyNoInteractions(categoryService);
    }

    public static class AnnotatedBusiness {
        static boolean returned;

        @UpdateCategoryTreeRedisCacheAnnotation
        public Result execute(Result result, boolean fail) {
            returned = false;
            if (fail) throw new IllegalStateException("写库失败");
            returned = true;
            return result;
        }
    }

    private static class SavingService extends CategoryServiceImpl {
        Category saved;
        boolean saveSuccess = true;

        @Override
        public boolean save(Category category) {
            saved = category;
            return saveSuccess;
        }
    }
}
