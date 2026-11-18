package com.leetcodeted.notification.repository;

import com.leetcodeted.notification.model.BoardMember;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BoardMemberRepository extends MongoRepository<BoardMember, String> {

    List<BoardMember> findAllByEmailIsNotNull();
}
