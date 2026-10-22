package com.cooked.backend.service.impl;

import com.cooked.backend.dto.request.BatchReassignRequest;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.repository.*;
import com.cooked.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchReassignTest {

    @Mock private RecipeAssignmentRepository assignments;
    @Mock private RecipeAssignmentHistoryRepository history;
    @Mock private RecipeRepository recipes;
    @Mock private UserRepository users;
    @Mock private NotificationService notifications;
    @Mock private SimpMessagingTemplate messaging;
    @InjectMocks private RecipeAssignmentServiceImpl service;

    private final UUID fromId = UUID.randomUUID(), toId = UUID.randomUUID();
    private final User admin = User.builder().email("boss@cooked.app").role(Role.ADMIN).build();
    private final User awa = User.builder().id(fromId).firstname("Awa").role(Role.EDITOR).build();
    private final User omar = User.builder().id(toId).firstname("Omar").role(Role.EDITOR).build();

    private void users() {
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(admin));
        when(users.findById(fromId)).thenReturn(Optional.of(awa));
        when(users.findById(toId)).thenReturn(Optional.of(omar));
    }

    @Test
    void movesTheRequestedNumberToTheOtherIntern() {
        users();
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).assignedToUser(awa).status(AssignmentStatus.ASSIGNED).build();
        RecipeAssignment b = RecipeAssignment.builder().id(UUID.randomUUID()).assignedToUser(awa).status(AssignmentStatus.IN_PROGRESS).build();
        when(assignments.findMovable(eq(fromId), any(), any())).thenReturn(List.of(a, b));
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));

        var res = service.reassignBatch(new BatchReassignRequest(fromId, toId, 2), "boss@cooked.app");

        assertEquals(2, res.moved());
        assertEquals(1, res.notStarted());
        assertEquals(1, res.inProgress());
        assertSame(omar, a.getAssignedToUser());
        assertEquals(AssignmentStatus.ASSIGNED, b.getStatus());
        verify(history, times(2)).save(any());
        verify(notifications, times(1)).createAndSendNotification(eq(omar), eq(admin), anyString(), contains("2 recette"), eq("ASSIGNMENT"), isNull(), isNull());
    }

    @Test
    void refusesWhenNotEnoughOrSameIntern() {
        users();
        when(assignments.findMovable(eq(fromId), any(), any())).thenReturn(List.of());
        assertThrows(BadRequestException.class, () -> service.reassignBatch(new BatchReassignRequest(fromId, toId, 3), "boss@cooked.app"));
        assertThrows(BadRequestException.class, () -> service.reassignBatch(new BatchReassignRequest(fromId, fromId, 1), "boss@cooked.app"));
        verify(assignments, never()).save(any());
    }

    @Test
    void refusesMoreThanMaxBatch() {
        assertThrows(BadRequestException.class, () -> service.reassignBatch(new BatchReassignRequest(fromId, toId, 501), "boss@cooked.app"));
        assertThrows(BadRequestException.class, () -> service.assignBatchByCount(
                com.cooked.backend.dto.request.BatchAssignmentRequest.builder().userId(toId).count(501).build(), "boss@cooked.app"));
        verify(assignments, never()).save(any());
    }
    @Test
    void batchAssignTopsUpWithNotStartedRecipesWhenAsked() {
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(admin));
        when(users.findById(toId)).thenReturn(Optional.of(omar));
        Recipe free = Recipe.builder().id(UUID.randomUUID()).name("Free").build();
        Recipe held = Recipe.builder().id(UUID.randomUUID()).name("Held").build();
        RecipeAssignment awaHolds = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(held).assignedToUser(awa).status(AssignmentStatus.IN_PROGRESS).build();
        when(recipes.findUnassignedUnmodifiedRecipes(any())).thenReturn(List.of(free));
        when(assignments.findReassignableUnmodified(eq(toId), any())).thenReturn(List.of(awaHolds, awaHolds));
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));

        var res = service.assignBatchByCount(com.cooked.backend.dto.request.BatchAssignmentRequest.builder()
                .userId(toId).count(2).includeAssigned(true).build(), "boss@cooked.app");

        assertEquals(2, res.size());
        assertSame(omar, awaHolds.getAssignedToUser());
        assertEquals(AssignmentStatus.ASSIGNED, awaHolds.getStatus());
        verify(assignments, times(2)).save(any()); // one moved row + one new row, no duplicate for the same recipe
    }

    @Test
    void batchAssignWithoutOptionNeverTouchesHeldRecipes() {
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(admin));
        when(users.findById(toId)).thenReturn(Optional.of(omar));
        when(recipes.findUnassignedUnmodifiedRecipes(any())).thenReturn(List.of());

        var ex = assertThrows(BadRequestException.class, () -> service.assignBatchByCount(
                com.cooked.backend.dto.request.BatchAssignmentRequest.builder().userId(toId).count(1).build(), "boss@cooked.app"));
        assertTrue(ex.getMessage().contains("seulement 0"));
        verify(assignments, never()).findReassignableUnmodified(any(), any());
        verify(assignments, never()).save(any());
    }

    @Test
    void singleReassignKeepsThePreviousStatusInHistory() {
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(admin));
        when(users.findById(toId)).thenReturn(Optional.of(omar));
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).recipe(Recipe.builder().name("R").build())
                .assignedToUser(awa).status(AssignmentStatus.IN_PROGRESS).build();
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));
        when(assignments.save(any())).thenAnswer(i -> i.getArgument(0));

        service.reassignAssignment(a.getId(), toId, "boss@cooked.app");

        var captor = org.mockito.ArgumentCaptor.forClass(RecipeAssignmentHistory.class);
        verify(history).save(captor.capture());
        assertEquals(AssignmentStatus.IN_PROGRESS, captor.getValue().getPreviousStatus());
        assertSame(omar, a.getAssignedToUser());
    }

    @Test
    void singleReassignRefusesNonIntern() {
        when(users.findByEmail("boss@cooked.app")).thenReturn(Optional.of(admin));
        UUID adminId = UUID.randomUUID();
        when(users.findById(adminId)).thenReturn(Optional.of(admin));
        RecipeAssignment a = RecipeAssignment.builder().id(UUID.randomUUID()).assignedToUser(awa).status(AssignmentStatus.ASSIGNED).build();
        when(assignments.findById(a.getId())).thenReturn(Optional.of(a));

        assertThrows(BadRequestException.class, () -> service.reassignAssignment(a.getId(), adminId, "boss@cooked.app"));
        verify(assignments, never()).save(any());
    }
}
